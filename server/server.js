// Taksi serveri — tashqi kutubxonasiz (faqat Node.js 18+).
// Ishga tushirish: node server.js
const http = require('http'), fs = require('fs'), crypto = require('crypto'), path = require('path');

const PORT = +process.env.PORT || 3000;
const DEV_SMS = process.env.DEV_SMS !== '0';           // true: kod javobda qaytariladi (SMS yo'q)
const PAY_DEV = process.env.PAY_DEV === '1';           // faqat maxsus sinov muhiti: haqiqiy pul yechilmaydi
// ---- Haqiqiy SMS (Eskiz.uz). ESKIZ_EMAIL va ESKIZ_PASSWORD berilsa yoqiladi; bo'lmasa eski rejim ----
const ESKIZ_EMAIL = process.env.ESKIZ_EMAIL || '', ESKIZ_PASSWORD = process.env.ESKIZ_PASSWORD || '';
const ESKIZ_URL = process.env.ESKIZ_URL || 'https://notify.eskiz.uz/api';
const SMS_FROM = process.env.SMS_FROM || '4546';
const SMS_TEXT = process.env.SMS_TEXT || 'TOXA: tasdiqlash kodi {code}';   // matn Eskiz'da tasdiqlangan shablonga mos bo'lsin
const SMS_ENABLED = !!(ESKIZ_EMAIL && ESKIZ_PASSWORD);
// Admin huquqi faqat shu raqamlarga beriladi (vergul bilan). Bo'sh bo'lsa, admin kirishi butunlay o'chiq.
const ADMIN_PHONES = (process.env.ADMIN_PHONES || '').split(',').map(x => x.trim()).filter(Boolean);
const DB_FILE = process.env.DB_FILE || path.join(__dirname, 'db.json');

let db = { users: {}, sessions: {}, codes: {}, orders: [], seq: 1, tickets: [], tseq: 1 };
try { db = Object.assign(db, JSON.parse(fs.readFileSync(DB_FILE, 'utf8'))); } catch {}
const save = () => fs.writeFileSync(DB_FILE, JSON.stringify(db));

let eskizToken = null;
async function eskizLogin() {
  const fd = new FormData(); fd.append('email', ESKIZ_EMAIL); fd.append('password', ESKIZ_PASSWORD);
  const r = await fetch(ESKIZ_URL + '/auth/login', { method: 'POST', body: fd });
  const j = await r.json().catch(() => ({}));
  eskizToken = j && j.data && j.data.token;
  if (!eskizToken) throw new Error('Eskiz login xatosi (' + r.status + ')');
}
async function sendSms(phone, text) {
  for (let attempt = 0; attempt < 2; attempt++) {
    if (!eskizToken) await eskizLogin();
    const fd = new FormData();
    fd.append('mobile_phone', phone.replace('+', '')); fd.append('message', text); fd.append('from', SMS_FROM);
    const r = await fetch(ESKIZ_URL + '/message/sms/send', { method: 'POST', headers: { Authorization: 'Bearer ' + eskizToken }, body: fd });
    if (r.status === 401) { eskizToken = null; continue; }   // token eskirgan: qayta kirib, yana urinamiz
    if (!r.ok) throw new Error('Eskiz SMS xatosi (' + r.status + ')');
    return;
  }
  throw new Error('Eskiz avtorizatsiya xatosi');
}

// ---- Narx ----
const BASE = 5000, PER_KM = 2500, MIN_FARE = 8000;
const rad = x => x * Math.PI / 180;
function distKm(a, b, c, d) {
  const dl = rad(c - a), dn = rad(d - b);
  const h = Math.sin(dl / 2) ** 2 + Math.cos(rad(a)) * Math.cos(rad(c)) * Math.sin(dn / 2) ** 2;
  return 2 * 6371 * Math.asin(Math.sqrt(h)) * 1.3;      // 1.3 — yo'l egriligi koeffitsiyenti
}
// kmHint: mijoz OSRM'dan olgan haqiqiy yo'l masofasi. Faqat oqilona oraliqda qabul qilinadi:
// to'g'ri chiziqdan (haversine) kichik bo'lmasligi va 4 baravardan oshmasligi kerak.
function quote(a, b, c, d, kmHint) {
  const straight = distKm(a, b, c, d) / 1.3;
  let raw = distKm(a, b, c, d);
  if (typeof kmHint === 'number' && isFinite(kmHint) && kmHint >= straight && kmHint <= straight * 4 + 2) raw = kmHint;
  const km = Math.max(0.1, Math.round(raw * 10) / 10);
  const price = Math.max(MIN_FARE, Math.round((BASE + km * PER_KM) / 500) * 500);
  return { km, price };
}

// ---- Yordamchilar ----
const send = (res, code, obj) => { res.writeHead(code, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(obj)); };
const readBody = req => new Promise(r => {
  let s = '';
  req.on('data', c => { s += c; if (s.length > 1e5) req.destroy(); });
  req.on('end', () => { try { r(JSON.parse(s || '{}')); } catch { r({}); } });
});
// Maxfiylik: buyurtma obyektida tomonlarning telefon raqami hech qachon uzatilmaydi.
const pub = u => u && { name: u.name };
const fresh = u => (u && u.loc && Date.now() - u.loc.t < 120000) ? { lat: u.loc.lat, lng: u.loc.lng } : undefined;
const ACTIVE = ['searching', 'accepted', 'arrived', 'started'];
const LIVE = ['accepted', 'arrived', 'started'];       // joylashuv faqat shu paytda ikki tomonga ko'rinadi
const isActive = o => ACTIVE.includes(o.status);
const view = o => {
  const live = LIVE.includes(o.status);
  return {
    ...o,
    passenger: pub(db.users[o.passengerId]),
    driver: pub(db.users[o.driverId]),
    driverLoc: live ? fresh(db.users[o.driverId]) : undefined,
    passengerLoc: live ? fresh(db.users[o.passengerId]) : undefined,
  };
};
const num = v => (typeof v === 'number' && isFinite(v)) ? v : NaN;
const PHONE_RE = /^\+998\d{9}$/;

http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://x'), p = url.pathname, m = req.method;
    const b = m === 'POST' ? await readBody(req) : {};

    // ---- Kirish (telefon + kod) ----
    if (m === 'POST' && p === '/auth/request') {
      const phone = String(b.phone || '').trim();
      if (!PHONE_RE.test(phone)) return send(res, 400, { error: 'Telefon raqam +998XXXXXXXXX ko\'rinishida bo\'lsin' });
      // Haqiqiy SMS yoqilgan bo'lsa: bir raqamga 60 soniyada bir martadan ko'p yuborilmaydi (pul va spamdan himoya)
      const prev = db.codes[phone];
      if (SMS_ENABLED && prev && prev.sent && Date.now() - prev.sent < 60000) {
        return send(res, 429, { error: 'Kodni 60 soniyadan keyin qayta so\'rang' });
      }
      const code = String(crypto.randomInt(100000, 1000000));
      db.codes[phone] = { code, exp: Date.now() + 5 * 60 * 1000, tries: 0, sent: Date.now() };
      save();
      if (SMS_ENABLED) {
        try { await sendSms(phone, SMS_TEXT.replace('{code}', code)); }
        catch (e) {
          console.error('[SMS xato]', e.message);
          delete db.codes[phone]; save();
          return send(res, 502, { error: 'SMS yuborilmadi, birozdan keyin urinib ko\'ring' });
        }
        return send(res, 200, { ok: true });          // SMS ketdi: kod hech qachon javobda qaytmaydi
      }
      console.log(`[SMS] ${phone}: ${code}`);          // SMS ulanmagan (sinov rejimi)
      return send(res, 200, DEV_SMS ? { ok: true, devCode: code } : { ok: true });
    }
    if (m === 'POST' && p === '/auth/verify') {
      const phone = String(b.phone || '').trim(), c = db.codes[phone];
      if (!c || c.exp < Date.now() || c.tries >= 5) return send(res, 400, { error: 'Kod eskirgan, qaytadan so\'rang' });
      if (String(b.code) !== c.code) { c.tries++; save(); return send(res, 400, { error: 'Kod noto\'g\'ri' }); }
      delete db.codes[phone];
      const adminLogin = b.admin === true;
      if (adminLogin && !ADMIN_PHONES.includes(phone)) return send(res, 403, { error: 'Bu raqamga admin huquqi berilmagan' });
      let user = Object.values(db.users).find(u => u.phone === phone);
      if (user && user.blocked) return send(res, 403, { error: 'Hisobingiz bloklangan' });
      if (user && user.role === 'admin' && !adminLogin) return send(res, 403, { error: 'Bu hisob faqat admin ilovasi uchun' });
      if (!user) {
        const name = String(b.name || '').trim().slice(0, 40);
        if (!name) return send(res, 400, { error: 'Ismingizni kiriting' });
        const role = adminLogin ? 'admin' : (b.role === 'driver' ? 'driver' : 'passenger');
        user = { id: crypto.randomUUID(), phone, name, role };
        db.users[user.id] = user;
      } else if (adminLogin) {
        user.role = 'admin';
      }
      const token = crypto.randomBytes(24).toString('hex');
      db.sessions[token] = user.id;
      save();
      return send(res, 200, { token, user: { name: user.name, phone: user.phone, role: user.role } });
    }

    // ---- Quyida hammasi kirishni talab qiladi ----
    const token = (req.headers.authorization || '').replace('Bearer ', '');
    const user = db.users[db.sessions[token]];
    if (!user) return send(res, 401, { error: 'Qaytadan kiring' });
    if (user.blocked) return send(res, 403, { error: 'Hisobingiz bloklangan' });

    // ---- Admin boshqaruvi (faqat role=admin). Hech narsa o'chirilmaydi: faqat ko'rish, bekor qilish va bloklash. ----
    if (p.startsWith('/admin/')) {
      if (user.role !== 'admin') return send(res, 403, { error: 'Faqat admin' });
      const all = Object.values(db.users);
      const sum = a => a.reduce((s, o) => s + o.price, 0);
      const adminOrder = o => ({
        ...o,
        passenger: db.users[o.passengerId] && { name: db.users[o.passengerId].name, phone: db.users[o.passengerId].phone },
        driver: db.users[o.driverId] && { name: db.users[o.driverId].name, phone: db.users[o.driverId].phone },
      });

      if (m === 'GET' && p === '/admin/overview') {
        const done = db.orders.filter(o => o.status === 'done');
        const today = new Date().toDateString();
        const t = done.filter(o => new Date(o.doneAt).toDateString() === today);
        const by = {};
        for (const o of db.orders) by[o.status] = (by[o.status] || 0) + 1;
        return send(res, 200, {
          users: {
            passengers: all.filter(u => u.role === 'passenger').length,
            drivers: all.filter(u => u.role === 'driver').length,
            onlineDrivers: all.filter(u => u.role === 'driver' && u.online === true).length,
            blocked: all.filter(u => u.blocked).length,
          },
          orders: { total: db.orders.length, active: db.orders.filter(isActive).length, byStatus: by },
          today: { count: t.length, sum: sum(t), cash: sum(t.filter(o => o.payMethod === 'cash')), card: sum(t.filter(o => o.payMethod === 'card')) },
          total: { count: done.length, sum: sum(done) },
        });
      }

      if (m === 'GET' && p === '/admin/users') {
        const role = url.searchParams.get('role'), q = (url.searchParams.get('q') || '').toLowerCase();
        const list = all.filter(u => u.role !== 'admin' && (!role || u.role === role) &&
          (!q || (u.name || '').toLowerCase().includes(q) || (u.phone || '').includes(q)))
          .sort((x, y) => (x.name || '').localeCompare(y.name || '')).slice(0, 100)
          .map(u => ({
            id: u.id, name: u.name, phone: u.phone, role: u.role, online: u.online === true, blocked: !!u.blocked,
            rating: Number(u.rating) || 0, ratingCount: Number(u.ratingCount) || 0,
            trips: db.orders.filter(o => o.status === 'done' && (o.passengerId === u.id || o.driverId === u.id)).length,
          }));
        return send(res, 200, { users: list });
      }

      const bm = p.match(/^\/admin\/users\/([\w-]+)\/block$/);
      if (m === 'POST' && bm) {
        const target = db.users[bm[1]];
        if (!target) return send(res, 404, { error: 'Foydalanuvchi topilmadi' });
        if (target.role === 'admin') return send(res, 400, { error: 'Adminni bloklab bo\'lmaydi' });
        target.blocked = b.blocked === true;
        if (target.blocked) target.online = false;
        save();
        return send(res, 200, { ok: true, blocked: target.blocked });
      }

      if (m === 'GET' && p === '/admin/orders') {
        const st = url.searchParams.get('status');
        const limit = Math.min(100, Math.max(1, parseInt(url.searchParams.get('limit')) || 50));
        const list = db.orders.filter(o => !st || (st === 'active' ? isActive(o) : o.status === st))
          .sort((x, y) => y.id - x.id).slice(0, limit).map(adminOrder);
        return send(res, 200, { orders: list });
      }

      const cm = p.match(/^\/admin\/orders\/(\d+)\/cancel$/);
      if (m === 'POST' && cm) {
        const o = db.orders.find(x => x.id === +cm[1]);
        if (!o) return send(res, 404, { error: 'Buyurtma topilmadi' });
        if (!isActive(o)) return send(res, 400, { error: 'Bu buyurtma allaqachon yakunlangan' });
        o.status = 'cancelled'; o.cancelledBy = 'admin'; save();
        return send(res, 200, { order: adminOrder(o) });
      }
      if (m === 'GET' && p === '/admin/tickets') {
        const st = url.searchParams.get('status');
        const list = db.tickets.filter(t => !st || t.status === st).sort((x, y) => y.id - x.id).slice(0, 100).map(t => {
          const u = db.users[t.userId];
          return { ...t, name: u && u.name, phone: u && u.phone, role: u && u.role };
        });
        return send(res, 200, { tickets: list });
      }
      const tm = p.match(/^\/admin\/tickets\/(\d+)\/reply$/);
      if (m === 'POST' && tm) {
        const t = db.tickets.find(x => x.id === +tm[1]);
        if (!t) return send(res, 404, { error: 'Murojaat topilmadi' });
        const text = String(b.text || '').trim().slice(0, 500);
        if (text.length < 2) return send(res, 400, { error: 'Javobni yozing' });
        t.reply = text; t.repliedAt = Date.now(); t.status = 'closed'; save();
        return send(res, 200, { ticket: t });
      }
      return send(res, 404, { error: 'Topilmadi' });
    }

    // Qo'llab-quvvatlash: foydalanuvchi murojaati (admin ilovasida ko'rinadi va javob beriladi)
    if (m === 'POST' && p === '/support') {
      const text = String(b.text || '').trim().slice(0, 500);
      if (text.length < 3) return send(res, 400, { error: 'Xabarni yozing (kamida 3 belgi)' });
      if (db.tickets.filter(t => t.userId === user.id && t.status === 'open').length >= 3) {
        return send(res, 400, { error: 'Avvalgi murojaatlaringizga javob kelishini kuting' });
      }
      const t = { id: db.tseq++, userId: user.id, text, status: 'open', createdAt: Date.now() };
      db.tickets.push(t); save();
      return send(res, 200, { ticket: t });
    }
    if (m === 'GET' && p === '/support/mine') {
      const list = db.tickets.filter(t => t.userId === user.id).sort((x, y) => y.id - x.id).slice(0, 20);
      return send(res, 200, { tickets: list });
    }

    if (m === 'GET' && p === '/profile') {
      return send(res, 200, {
        name: user.name || '',
        phone: user.phone || '',
        role: user.role || '',
      });
    }

    if (m === 'GET' && p === '/estimate') {
      const [a, b2, c, d] = ['flat', 'flng', 'tlat', 'tlng'].map(k => parseFloat(url.searchParams.get(k)));
      if ([a, b2, c, d].some(isNaN)) return send(res, 400, { error: 'Koordinata xato' });
      return send(res, 200, quote(a, b2, c, d, parseFloat(url.searchParams.get('km'))));
    }

    // Haydovchi ish holati: onlayn bo'lmasa yangi buyurtmalar ko'rsatilmaydi.
    if (m === 'POST' && p === '/driver/availability') {
      if (user.role !== 'driver') return send(res, 403, { error: 'Faqat haydovchi' });
      const online = b.online === true;
      if (!online && db.orders.some(o => o.driverId === user.id && ['accepted', 'arrived', 'started'].includes(o.status))) {
        return send(res, 400, { error: "Faol safar tugamaguncha ishdan chiqa olmaysiz" });
      }
      user.online = online;
      save();
      return send(res, 200, { ok: true, online: user.online });
    }

    // Joriy joylashuvni yangilash (yo'lovchi ham, haydovchi ham)
    if (m === 'POST' && p === '/location') {
      const lat = num(b.lat), lng = num(b.lng);
      if (isNaN(lat) || isNaN(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) return send(res, 400, { error: 'Koordinata xato' });
      user.loc = { lat, lng, t: Date.now() };
      save();
      return send(res, 200, { ok: true });
    }

    // Yo'lovchi: buyurtma berish
    if (m === 'POST' && p === '/orders') {
      if (user.role !== 'passenger') return send(res, 403, { error: 'Faqat yo\'lovchi buyurtma bera oladi' });
      if (db.orders.some(o => o.passengerId === user.id && isActive(o))) return send(res, 400, { error: 'Sizda faol buyurtma bor' });
      const [a, b2, c, d] = [num(b.fromLat), num(b.fromLng), num(b.toLat), num(b.toLng)];
      if ([a, b2, c, d].some(isNaN) || !b.from || !b.to) return send(res, 400, { error: 'Manzil to\'liq emas' });
      if (b.payMethod === 'card' && !PAY_DEV) return send(res, 400, { error: 'Karta to\'lovi hali ulanmagan. Naqd to\'lovni tanlang' });
      const q = quote(a, b2, c, d, num(b.km));
      const o = {
        id: db.seq++, passengerId: user.id, driverId: null, status: 'searching',
        from: String(b.from).slice(0, 120), to: String(b.to).slice(0, 120),
        fromLat: a, fromLng: b2, toLat: c, toLng: d, km: q.km, price: q.price, createdAt: Date.now(),
        payMethod: b.payMethod === 'card' ? 'card' : 'cash', payStatus: 'unpaid',
      };
      db.orders.push(o); save();
      return send(res, 200, { order: view(o) });
    }

    // Mening buyurtmalarim (yo'lovchi yoki haydovchi)
    if (m === 'GET' && p === '/orders/mine') {
      const key = user.role === 'driver' ? 'driverId' : 'passengerId';
      const list = db.orders.filter(o => o[key] === user.id).sort((x, y) => y.id - x.id).slice(0, 30).map(view);
      return send(res, 200, { orders: list });
    }

    // Haydovchi: ochiq buyurtmalar (yo'lovchigacha masofa bilan)
    if (m === 'GET' && p === '/orders/available') {
      if (user.role !== 'driver') return send(res, 403, { error: 'Faqat haydovchi' });
      if (user.online !== true) return send(res, 200, { orders: [] });
      const me = fresh(user);
      const list = db.orders.filter(o => o.status === 'searching').sort((x, y) => y.id - x.id).slice(0, 20).map(o => ({
        ...view(o),
        pickupKm: me ? Math.round(distKm(me.lat, me.lng, o.fromLat, o.fromLng) * 10) / 10 : undefined,
      }));
      return send(res, 200, { orders: list });
    }

    // Haydovchi statistikasi
    if (m === 'GET' && p === '/stats') {
      if (user.role !== 'driver') return send(res, 403, { error: 'Faqat haydovchi' });
      const done = db.orders.filter(o => o.driverId === user.id && o.status === 'done');
      const today = new Date().toDateString();
      const t = done.filter(o => new Date(o.doneAt).toDateString() === today);
      const sum = a => a.reduce((s, o) => s + o.price, 0);
      return send(res, 200, {
        todayCount: t.length, todaySum: sum(t),
        todayCash: sum(t.filter(o => o.payMethod === 'cash')),
        todayCard: sum(t.filter(o => o.payMethod === 'card')),
        totalCount: done.length, totalSum: sum(done),
        online: user.online === true,
        rating: Number(user.rating) || 0, ratingCount: Number(user.ratingCount) || 0,
      });
    }

    const mm = p.match(/^\/orders\/(\d+)\/(accept|status|cancel|pay|rate)$/);
    if (m === 'POST' && mm) {
      const o = db.orders.find(x => x.id === +mm[1]);
      if (!o) return send(res, 404, { error: 'Buyurtma topilmadi' });
      const act = mm[2];

      if (act === 'accept') {
        if (user.role !== 'driver') return send(res, 403, { error: 'Faqat haydovchi' });
        if (user.online !== true) return send(res, 400, { error: 'Avval ishga chiqing' });
        if (o.status !== 'searching') return send(res, 400, { error: 'Buyurtma allaqachon olingan' });
        if (db.orders.some(x => x.driverId === user.id && isActive(x))) return send(res, 400, { error: 'Avval joriy buyurtmani tugating' });
        o.driverId = user.id; o.status = 'accepted'; save();
        return send(res, 200, { order: view(o) });
      }
      if (act === 'status') {
        const next = { accepted: 'arrived', arrived: 'started', started: 'done' };
        if (o.driverId !== user.id) return send(res, 403, { error: 'Bu sizning buyurtmangiz emas' });
        if (next[o.status] !== b.status) return send(res, 400, { error: 'Noto\'g\'ri holat' });
        o.status = b.status;
        if (b.status === 'done') {
          o.doneAt = Date.now();
          if (o.payMethod === 'cash') { o.payStatus = 'paid'; o.paidAt = o.doneAt; }   // naqd: haydovchi pulni oldi
        }
        save();
        return send(res, 200, { order: view(o) });
      }
      if (act === 'cancel') {
        if (o.passengerId === user.id && ['searching', 'accepted', 'arrived'].includes(o.status)) {
          o.status = 'cancelled'; save(); return send(res, 200, { order: view(o) });
        }
        if (o.driverId === user.id && ['accepted', 'arrived'].includes(o.status)) {
          o.driverId = null; o.status = 'searching'; save(); return send(res, 200, { order: view(o) });
        }
        return send(res, 400, { error: 'Bekor qilib bo\'lmaydi' });
      }
      if (act === 'rate') {
        if (o.passengerId !== user.id) return send(res, 403, { error: 'Bu sizning buyurtmangiz emas' });
        if (o.status !== 'done' || !o.driverId) return send(res, 400, { error: 'Faqat yakunlangan safarni baholash mumkin' });
        if (o.rating) return send(res, 400, { error: 'Bu safar allaqachon baholangan' });
        const r = Math.round(num(b.rating));
        if (!(r >= 1 && r <= 5)) return send(res, 400, { error: 'Baho 1 dan 5 gacha bo\'lsin' });
        const d = db.users[o.driverId];
        o.rating = r;
        if (d) {
          const n = Number(d.ratingCount) || 0;
          d.rating = Math.round((((Number(d.rating) || 0) * n + r) / (n + 1)) * 100) / 100;
          d.ratingCount = n + 1;
        }
        save();
        return send(res, 200, { order: view(o) });
      }
      if (act === 'pay') {
        if (o.passengerId !== user.id) return send(res, 403, { error: 'Bu sizning buyurtmangiz emas' });
        if (o.payMethod !== 'card') return send(res, 400, { error: 'Bu buyurtma naqd to\'lanadi' });
        if (o.status !== 'done') return send(res, 400, { error: 'Safar tugagach to\'lanadi' });
        if (o.payStatus === 'paid') return send(res, 400, { error: 'Allaqachon to\'langan' });
        if (PAY_DEV) {                                  // Faqat PAY_DEV=1 bilan yoqiladigan sinov rejimi
          o.payStatus = 'paid'; o.paidAt = Date.now(); save();
          return send(res, 200, { order: view(o), simulated: true });
        }
        // TODO: Payme / Click / Uzum Merchant API: to'lov sessiyasini yarating va payUrl qaytaring.
        // To'lov tasdig'i provayderning webhook'i orqali kelib, payStatus='paid' qilinishi kerak.
        return send(res, 501, { error: 'Karta to\'lovi provayderi hali ulanmagan' });
      }
    }

    send(res, 404, { error: 'Topilmadi' });
  } catch (e) {
    console.error(e);
    send(res, 500, { error: 'Server xatosi' });
  }
}).listen(PORT, () => console.log(`Taksi serveri: http://0.0.0.0:${PORT}`));
