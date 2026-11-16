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
  }

  console.log(fails ? `\n${fails} ta xato` : '\nHammasi o\'tdi');
  process.exit(fails ? 1 : 0);
})();
