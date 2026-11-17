// To'liq oqimni tekshiradi: node test.js  (server alohida ishlab turishi kerak)
const B = process.env.BASE || 'http://localhost:3999';
let fails = 0;
const ok = (c, n) => { console.log((c ? 'OK   ' : 'FAIL ') + n); if (!c) fails++; };
async function call(m, p, body, tok) {
  const r = await fetch(B + p, { method: m, headers: { 'Content-Type': 'application/json', ...(tok ? { Authorization: 'Bearer ' + tok } : {}) }, body: body ? JSON.stringify(body) : undefined });
  return { s: r.status, j: await r.json() };
}
async function login(phone, name, role) {
  const a = await call('POST', '/auth/request', { phone });
  const v = await call('POST', '/auth/verify', { phone, code: a.j.devCode, name, role });
  return v.j.token;
}
(async () => {
  ok((await call('POST', '/auth/request', { phone: '123' })).s === 400, 'noto\'g\'ri telefon rad etildi');
  await call('POST', '/auth/request', { phone: '+998901112233' });
  ok((await call('POST', '/auth/verify', { phone: '+998901112233', code: '000000', name: 'X' })).s === 400, 'noto\'g\'ri kod rad etildi');
  const P = await login('+998901112233', 'Ali', 'passenger');
  const D = await login('+998907778899', 'Vali', 'driver');
  ok(P && D, 'ro\'yxatdan o\'tish');
  ok((await call('GET', '/orders/available', null, D)).j.orders.length === 0, 'oflayn haydovchi buyurtma ko\'rmaydi');
  ok((await call('POST', '/driver/availability', { online: true }, D)).j.online === true, 'haydovchi ishga chiqdi');
  ok((await call('GET', '/orders/mine')).s === 401, 'tokensiz 401');

  const q = await call('GET', '/estimate?flat=41.311&flng=69.279&tlat=41.340&tlng=69.285', null, P);
  ok(q.j.price >= 8000 && q.j.km > 0, 'narx hisoblandi: ' + JSON.stringify(q.j));

  // joylashuv
  ok((await call('POST', '/location', { lat: 999, lng: 0 }, P)).s === 400, 'noto\'g\'ri koordinata rad etildi');
  ok((await call('POST', '/location', { lat: 41.311, lng: 69.279 }, P)).s === 200, 'yo\'lovchi joylashuvi yuborildi');
  ok((await call('POST', '/location', { lat: 41.320, lng: 69.285 }, D)).s === 200, 'haydovchi joylashuvi yuborildi');

  const card = { from: 'A', to: 'B', fromLat: 41.311, fromLng: 69.279, toLat: 41.34, toLng: 69.285, payMethod: 'card' };
  const o = await call('POST', '/orders', card, P);
  ok(o.s === 200 && o.j.order.status === 'searching' && o.j.order.payMethod === 'card', 'karta buyurtmasi yaratildi');
  ok(o.j.order.driverLoc === undefined, 'qidiruv paytida joylashuv ko\'rinmaydi');
  ok((await call('POST', '/orders', card, P)).s === 400, 'ikkinchi faol buyurtma rad etildi');
  ok((await call('POST', '/orders', card, D)).s === 403, 'haydovchi buyurtma bera olmaydi');

  const id = o.j.order.id;
  const av = await call('GET', '/orders/available', null, D);
  ok(av.j.orders.length === 1 && av.j.orders[0].pickupKm > 0, 'haydovchi buyurtmani va yo\'lovchigacha masofani ko\'rdi: ' + av.j.orders[0].pickupKm + ' km');
  ok((await call('POST', `/orders/${id}/accept`, {}, D)).s === 200, 'haydovchi qabul qildi');
  ok((await call('POST', `/orders/${id}/accept`, {}, D)).s === 400, 'qayta qabul qilib bo\'lmaydi');

  const live = (await call('GET', '/orders/mine', null, P)).j.orders[0];
  ok(live.driverLoc && Math.abs(live.driverLoc.lat - 41.32) < 1e-9, 'yo\'lovchi haydovchi joylashuvini ko\'rdi');
  const liveD = (await call('GET', '/orders/mine', null, D)).j.orders[0];
  ok(liveD.passengerLoc && Math.abs(liveD.passengerLoc.lng - 69.279) < 1e-9, 'haydovchi yo\'lovchi joylashuvini ko\'rdi');

  ok((await call('POST', `/orders/${id}/status`, { status: 'done' }, D)).s === 400, 'bosqichni o\'tkazib bo\'lmaydi');
  ok((await call('POST', `/orders/${id}/pay`, {}, P)).s === 400, 'safar tugamasdan to\'lab bo\'lmaydi');
  for (const s of ['arrived', 'started', 'done']) ok((await call('POST', `/orders/${id}/status`, { status: s }, D)).s === 200, 'holat: ' + s);

  const fin = (await call('GET', '/orders/mine', null, P)).j.orders[0];
  ok(fin.payStatus === 'unpaid' && fin.driverLoc === undefined, 'karta: to\'lanmagan; safar tugagach joylashuv yashirildi');
  ok((await call('POST', `/orders/${id}/pay`, {}, D)).s === 403, 'haydovchi to\'lov qila olmaydi');
  const pay = await call('POST', `/orders/${id}/pay`, {}, P);
  ok(pay.s === 200 && pay.j.order.payStatus === 'paid', 'karta to\'lovi (sinov rejimi)');
  ok((await call('POST', `/orders/${id}/pay`, {}, P)).s === 400, 'ikki marta to\'lab bo\'lmaydi');

  // naqd
  const cash = await call('POST', '/orders', { ...card, payMethod: 'cash' }, P);
  const cid = cash.j.order.id;
  await call('POST', `/orders/${cid}/accept`, {}, D);
  for (const s of ['arrived', 'started', 'done']) await call('POST', `/orders/${cid}/status`, { status: s }, D);
  const cf = (await call('GET', '/orders/mine', null, P)).j.orders[0];
  ok(cf.payMethod === 'cash' && cf.payStatus === 'paid', 'naqd: safar tugagach avtomatik "to\'langan"');
  ok((await call('POST', `/orders/${cid}/pay`, {}, P)).s === 400, 'naqd buyurtmaga karta to\'lovi yo\'q');

  const st = await call('GET', '/stats', null, D);
  ok(st.j.todayCount === 2 && st.j.todayCash === q.j.price && st.j.todayCard === q.j.price && st.j.todaySum === 2 * q.j.price, 'statistika (naqd/karta): ' + JSON.stringify(st.j));

  // bekor qilish
  const o2 = await call('POST', '/orders', card, P);
  ok((await call('POST', `/orders/${o2.j.order.id}/cancel`, {}, P)).j.order.status === 'cancelled', 'yo\'lovchi bekor qildi');
  const o3 = await call('POST', '/orders', card, P);
  await call('POST', `/orders/${o3.j.order.id}/accept`, {}, D);
  ok((await call('POST', `/orders/${o3.j.order.id}/cancel`, {}, D)).j.order.status === 'searching', 'haydovchi rad etdi — qayta qidiruvga o\'tdi');

  // ---- Baholash ----
  await call('POST', `/orders/${o3.j.order.id}/cancel`, {}, P);
  const o4 = await call('POST', '/orders', { ...card, payMethod: 'cash' }, P);
  await call('POST', `/orders/${o4.j.order.id}/accept`, {}, D);
  await call('POST', `/orders/${o4.j.order.id}/status`, { status: 'arrived' }, D);
  await call('POST', `/orders/${o4.j.order.id}/status`, { status: 'started' }, D);
  ok((await call('POST', `/orders/${o4.j.order.id}/rate`, { rating: 5 }, P)).s === 400, 'tugamagan safarni baholab bo\'lmaydi');
  await call('POST', `/orders/${o4.j.order.id}/status`, { status: 'done' }, D);
  ok((await call('POST', `/orders/${o4.j.order.id}/rate`, { rating: 9 }, P)).s === 400, 'noto\'g\'ri baho rad etildi');
  ok((await call('POST', `/orders/${o4.j.order.id}/rate`, { rating: 4 }, P)).j.order.rating === 4, 'haydovchi baholandi');
  ok((await call('POST', `/orders/${o4.j.order.id}/rate`, { rating: 5 }, P)).s === 400, 'ikkinchi marta baholab bo\'lmaydi');
  ok((await call('GET', '/stats', null, D)).j.rating === 4, 'haydovchi reytingi yangilandi');

  // ---- Qo'llab-quvvatlash ----
  ok((await call('POST', '/support', { text: 'a' }, P)).s === 400, 'juda qisqa murojaat rad etildi');
  const tk = await call('POST', '/support', { text: 'Ilova haqida savol bor' }, P);
  ok(tk.s === 200 && tk.j.ticket.status === 'open', 'murojaat yuborildi');
  ok((await call('GET', '/support/mine', null, P)).j.tickets.length === 1, 'murojaatlarim ro\'yxati');
  if (process.env.ADMIN_PHONE) {
    const ra2 = await call('POST', '/auth/request', { phone: process.env.ADMIN_PHONE });
    const va2 = await call('POST', '/auth/verify', { phone: process.env.ADMIN_PHONE, code: ra2.j.devCode, name: 'Admin', admin: true });
    const A2 = va2.j.token;
    const tl = await call('GET', '/admin/tickets?status=open', null, A2);
    ok(tl.j.tickets.length === 1 && tl.j.tickets[0].phone, 'admin murojaatni ko\'rdi');
    ok((await call('POST', `/admin/tickets/${tl.j.tickets[0].id}/reply`, { text: 'Yordam beramiz' }, A2)).j.ticket.status === 'closed', 'admin javob berdi');
    ok((await call('GET', '/support/mine', null, P)).j.tickets[0].reply === 'Yordam beramiz', 'foydalanuvchi javobni ko\'rdi');
  }

  // ---- Admin (ADMIN_PHONES=+998900000000 bilan ishga tushirilgan serverda) ----
  const AP = process.env.ADMIN_PHONE;
  if (AP) {
    const a1 = await call('POST', '/auth/request', { phone: '+998905550000' });
    ok((await call('POST', '/auth/verify', { phone: '+998905550000', code: a1.j.devCode, name: 'Yot', admin: true })).s === 403, 'admin raqami bo\'lmaganga admin kirishi rad etildi');
    const ra = await call('POST', '/auth/request', { phone: AP });
    const va = await call('POST', '/auth/verify', { phone: AP, code: ra.j.devCode, name: 'Admin', admin: true });
    const A = va.j.token;
    ok(va.s === 200 && va.j.user.role === 'admin', 'admin kirdi');
    ok((await call('GET', '/admin/tariffs', null, P)).s === 403, 'tarif boshqaruvi oddiy foydalanuvchiga yopiq');
    const initialTariff = await call('GET', '/admin/tariffs', null, A);
    ok(initialTariff.s === 200 && initialTariff.j.tariffs.minimum === 8000, 'standart tariflar yuklandi');
    const savedTariff = await call('POST', '/admin/tariffs', { base: 10000, perKm: 3000, minimum: 12000 }, A);
    ok(savedTariff.s === 200 && savedTariff.j.tariffs.perKm === 3000, 'admin yangi tarifni saqladi');
    const updatedEstimate = await call('GET', '/estimate?flat=41.311&flng=69.279&tlat=41.340&tlng=69.285', null, P);
    ok(updatedEstimate.j.price > q.j.price, 'yangi tarif narx hisobiga qo‘llandi');
    ok((await call('POST', '/admin/tariffs', { base: -1, perKm: 0, minimum: 1 }, A)).s === 400, 'noto‘g‘ri tarif qiymatlari rad etildi');
    ok((await call('GET', '/admin/overview', null, P)).s === 403, 'oddiy foydalanuvchiga admin API yopiq');
    const ov = await call('GET', '/admin/overview', null, A);
    ok(ov.j.users.drivers >= 1 && ov.j.orders.total >= 1, 'umumiy ko\'rsatkichlar: ' + JSON.stringify(ov.j.users));
    const us = await call('GET', '/admin/users?role=driver', null, A);
    ok(us.j.users.length >= 1 && us.j.users[0].phone, 'haydovchilar ro\'yxati');
    await call('POST', '/orders', { ...card, payMethod: 'cash' }, P);
    const act = await call('GET', '/admin/orders?status=active', null, A);
    ok(act.j.orders.length >= 1 && act.j.orders[0].passenger.phone, 'faol buyurtmalar (telefon bilan)');
    const cx = await call('POST', `/admin/orders/${act.j.orders[0].id}/cancel`, {}, A);
    ok(cx.j.order && cx.j.order.status === 'cancelled', 'admin buyurtmani bekor qildi');
    const pid = us.j.users[0].id;
    ok((await call('POST', `/admin/users/${pid}/block`, { blocked: true }, A)).j.blocked === true, 'foydalanuvchi bloklandi');
    ok((await call('GET', '/orders/mine', null, D)).s === 403, 'bloklangan foydalanuvchi kira olmaydi');
    ok((await call('POST', `/admin/users/${pid}/block`, { blocked: false }, A)).j.blocked === false, 'blok olib tashlandi');
    ok((await call('GET', '/orders/mine', null, D)).s === 200, 'blokdan keyin yana ishlaydi');

    // ---- Haydovchi hujjatlari ----
    const jpg = Buffer.concat([Buffer.from([0xFF, 0xD8, 0xFF, 0xE0]), Buffer.alloc(2000, 7)]).toString('base64');
    ok((await call('POST', '/driver/docs', { kind: 'license', data: jpg }, P)).s === 403, 'yo\'lovchi hujjat yuklay olmaydi');
    ok((await call('POST', '/driver/docs', { kind: 'xxx', data: jpg }, D)).s === 400, 'noto\'g\'ri hujjat turi rad etildi');
    ok((await call('POST', '/driver/docs', { kind: 'license', data: Buffer.alloc(2000).toString('base64') }, D)).s === 400, 'JPEG bo\'lmagan fayl rad etildi');
    const up = await call('POST', '/driver/docs', { kind: 'license', data: jpg }, D);
    ok(up.s === 200 && up.j.docs.license.status === 'pending', 'guvohnoma yuklandi (kutilmoqda)');
    ok((await call('POST', '/driver/plate', { plate: '01 A 123 BC' }, D)).j.plate === '01 A 123 BC', 'mashina raqami saqlandi');
    const dl = await call('GET', '/admin/drivers?status=pending', null, A);
    const did = dl.j.drivers[0] && dl.j.drivers[0].id;
    ok(did && dl.j.drivers[0].docs.license.status === 'pending', 'admin kutayotgan hujjatni ko\'rdi');
    ok((await call('POST', `/admin/docs/${did}/license/review`, { approve: false }, A)).s === 400, 'sababsiz rad etib bo\'lmaydi');
    ok((await call('POST', `/admin/docs/${did}/license/review`, { approve: false, note: 'Xira rasm' }, A)).j.docs.license.status === 'rejected', 'hujjat rad etildi');
    ok((await call('GET', '/driver/docs', null, D)).j.docs.license.note === 'Xira rasm', 'haydovchi rad sababini ko\'rdi');
    ok((await call('POST', `/admin/docs/${did}/license/review`, { approve: true }, A)).j.docs.license.status === 'approved', 'hujjat tasdiqlandi');
    const img = await fetch(B + `/docs/${did}/license`, { headers: { Authorization: 'Bearer ' + A } });
    ok(img.status === 200 && img.headers.get('content-type') === 'image/jpeg', 'admin rasmni ko\'ra oladi');
    ok((await fetch(B + `/docs/${did}/license`, { headers: { Authorization: 'Bearer ' + P } })).status === 403, 'begona foydalanuvchi rasmni ko\'ra olmaydi');

    // ---- Yangi imkoniyatlar: bonus, balans, band qilish, shaharlararo, yo'qotilgan narsa ----
    const P2 = await login('+998905550001', 'Sardor', 'passenger');
    const D2 = await login('+998905550002', 'Bobur', 'driver');
    await call('POST', '/driver/availability', { online: true }, D2);
    await call('POST', '/location', { lat: 41.30, lng: 69.27 }, D2);
    await call('POST', '/location', { lat: 41.311, lng: 69.279 }, P2);
    const trip = { from: 'A', to: 'B', fromLat: 41.311, fromLng: 69.279, toLat: 41.34, toLng: 69.285, payMethod: 'cash' };
    async function fullTrip(pt, dt) {
      const x = await call('POST', '/orders', trip, pt);
      const oid = x.j.order.id;
      await call('POST', `/orders/${oid}/accept`, null, dt);
      for (const st of ['arrived', 'started', 'done']) await call('POST', `/orders/${oid}/status`, { status: st }, dt);
      return oid;
    }
    let lastId;
    for (let i = 0; i < 5; i++) lastId = await fullTrip(P2, D2);
    const bn = await call('GET', '/driver/bonus', null, D2);
    ok(bn.j.today === 5 && bn.j.tiers[0].claimed === true && bn.j.tiers[1].claimed === false, 'bonus: 5 safar uchun mukofot berildi');
    ok((await call('GET', '/driver/balance', null, D2)).j.balance === 10000, 'balansda 10 000 so\'m');
    ok((await call('POST', '/driver/withdraw', { amount: 10000, note: 'Karta 8600' }, D2)).s === 400, 'eng kam miqdordan kam yechib bo\'lmaydi');
    for (let i = 0; i < 5; i++) lastId = await fullTrip(P2, D2);
    ok((await call('GET', '/driver/balance', null, D2)).j.balance === 40000, '10 safar: jami 40 000 so\'m bonus');
    ok((await call('POST', '/driver/withdraw', { amount: 50000, note: 'Karta 8600' }, D2)).s === 400, 'balansdan ko\'p yechib bo\'lmaydi');
    ok((await call('POST', '/driver/withdraw', { amount: 20000 }, D2)).s === 400, 'qayerga yuborilishi shart');
    const w1 = await call('POST', '/driver/withdraw', { amount: 20000, note: 'Karta 8600 1234' }, D2);
    ok(w1.s === 200 && w1.j.balance === 20000, 'pul yechish so\'rovi yaratildi, balans kamaydi');
    const wl = await call('GET', '/admin/withdrawals', null, A);
    ok(wl.j.withdrawals.length >= 1 && wl.j.withdrawals[0].phone === '+998905550002', 'admin so\'rovni ko\'rdi');
    ok((await call('POST', `/admin/withdrawals/${w1.j.withdrawal.id}`, { action: 'reject', note: 'Xato karta' }, A)).s === 200, 'so\'rov rad etildi');
    ok((await call('GET', '/driver/balance', null, D2)).j.balance === 40000, 'rad etilganda pul qaytdi');
    const w2 = await call('POST', '/driver/withdraw', { amount: 20000, note: 'Karta 8600 1234' }, D2);
    ok((await call('POST', `/admin/withdrawals/${w2.j.withdrawal.id}`, { action: 'paid' }, A)).j.withdrawal.status === 'paid', 'so\'rov to\'landi deb belgilandi');
    ok((await call('POST', `/admin/withdrawals/${w2.j.withdrawal.id}`, { action: 'paid' }, A)).s === 400, 'qayta ko\'rib chiqib bo\'lmaydi');

    // yo'qotilgan narsa
    ok((await call('POST', `/orders/${lastId}/lost`, { text: 'Telefon' }, D2)).s === 403, 'haydovchi yo\'qotilgan narsa xabar bera olmaydi');
    ok((await call('POST', `/orders/${lastId}/lost`, { text: 'Qora sumka' }, P2)).j.order.lost.text === 'Qora sumka', 'yo\'qotilgan narsa xabari yuborildi');
    ok((await call('GET', '/orders/mine', null, D2)).j.orders.find(o => o.id === lastId).lostPhone === '+998905550001', 'haydovchi yo\'lovchi raqamini ko\'rdi');

    // shaharlararo
    const cities = await call('GET', '/intercity/cities', null, P2);
    ok(cities.j.cities.some(c => c.key === 'samarqand' && c.price === 210000), 'shaharlararo narx: Samarqand 210 000');
    ok((await call('POST', '/orders', { ...trip, kind: 'intercity', city: 'yoq', toLat: 39.65, toLng: 66.97 }, P2)).s === 400, 'noto\'g\'ri shahar rad etildi');
    const ic = await call('POST', '/orders', { ...trip, kind: 'intercity', city: 'samarqand', toLat: 39.654, toLng: 66.975, seats: 2, when: 'Ertaga 08:00' }, P2);
    ok(ic.s === 200 && ic.j.order.kind === 'intercity' && ic.j.order.price === 210000 && ic.j.order.toCity === 'Samarqand', 'shaharlararo buyurtma yaratildi');
    await call('POST', `/orders/${ic.j.order.id}/cancel`, null, P2);

    // yaqin haydovchi: uzoq haydovchi yangi buyurtmani darhol ko'rmaydi
    const DF = await login('+998905550003', 'Uzoq', 'driver');
    await call('POST', '/driver/availability', { online: true }, DF);
    await call('POST', '/location', { lat: 41.50, lng: 69.60 }, DF);
    const o3 = await call('POST', '/orders', trip, P2);
    ok((await call('GET', '/orders/available', null, DF)).j.orders.length === 0, 'uzoq haydovchi yangi buyurtmani darhol ko\'rmaydi');
    ok((await call('GET', '/orders/available', null, D2)).j.orders.some(o => o.id === o3.j.order.id), 'yaqin haydovchi buyurtmani darhol ko\'radi');

    // ketma-ket buyurtma (band qilish)
    ok((await call('POST', `/orders/${o3.j.order.id}/reserve`, null, D2)).s === 400, 'safar bo\'lmaganda band qilib bo\'lmaydi');
    await call('POST', `/orders/${o3.j.order.id}/accept`, null, D2);
    await call('POST', `/orders/${o3.j.order.id}/status`, { status: 'arrived' }, D2);
    await call('POST', `/orders/${o3.j.order.id}/status`, { status: 'started' }, D2);
    const P3 = await login('+998905550004', 'Dilnoza', 'passenger');
    await call('POST', '/location', { lat: 41.312, lng: 69.28 }, P3);
    const o4 = await call('POST', '/orders', trip, P3);
    ok((await call('POST', `/orders/${o4.j.order.id}/reserve`, null, D2)).s === 200, 'keyingi buyurtma band qilindi');
    ok((await call('POST', `/orders/${o4.j.order.id}/accept`, null, DF)).s === 400, 'band qilingan buyurtmani boshqa olmaydi');
    await call('POST', `/orders/${o3.j.order.id}/status`, { status: 'done' }, D2);
    const nxt = (await call('GET', '/orders/mine', null, D2)).j.orders.find(o => o.id === o4.j.order.id);
    ok(nxt && nxt.status === 'accepted', 'safar tugagach band qilingan buyurtma avtomatik qabul qilindi');

    // talab xaritasi
    const dm = await call('GET', '/driver/demand', null, D2);
    ok(dm.j.zones.length >= 1 && dm.j.zones[0].count >= 1, 'talab xaritasi zonalari qaytdi');
  }

  console.log(fails ? `\n${fails} ta xato` : '\nHammasi o\'tdi');
  process.exit(fails ? 1 : 0);
})();
