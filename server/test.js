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
  await call('POST', '/auth/request', { phone: '+998901112244' });
  ok((await call('POST', '/auth/verify', { phone: '+998901112244', code: '000000', name: 'X' })).s === 400, 'noto\'g\'ri kod rad etildi');
  const P = await login('+998901112233', 'Ali', 'passenger');
  const D = await login('+998907778899', 'Vali', 'driver');
  ok(P && D, 'ro\'yxatdan o\'tish');
  ok((await call('GET', '/orders/mine')).s === 401, 'tokensiz 401');

  const q = await call('GET', '/estimate?flat=41.311&flng=69.279&tlat=41.340&tlng=69.285', null, P);
  ok(q.j.price >= 8000 && q.j.km > 0, 'narx hisoblandi: ' + JSON.stringify(q.j));

  // joylashuv
  ok((await call('POST', '/location', { lat: 999, lng: 0 }, P)).s === 400, 'noto\'g\'ri koordinata rad etildi');
  ok((await call('POST', '/location', { lat: 41.311, lng: 69.279 }, P)).s === 200, 'yo\'lovchi joylashuvi yuborildi');
  ok((await call('POST', '/location', { lat: 41.320, lng: 69.285 }, D)).s === 200, 'haydovchi joylashuvi yuborildi');

  const card = { from: 'A', to: 'B', fromLat: 41.311, fromLng: 69.279, toLat: 41.34, toLng: 69.285, payMethod: 'card' };
  const o = await call('POST', '/orders', card, P);
  ok(o.s === 200 && o.j.order?.status === 'searching' && o.j.order?.payMethod === 'card', 'karta buyurtmasi yaratildi: ' + JSON.stringify(o.j));
  if (!o.j.order) process.exit(1);
  ok(o.j.order?.driverLoc === undefined, 'qidiruv paytida joylashuv ko\'rinmaydi');
  ok((await call('POST', '/orders', card, P)).s === 400, 'ikkinchi faol buyurtma rad etildi');
  ok((await call('POST', '/orders', card, D)).s === 403, 'haydovchi buyurtma bera olmaydi');
  ok((await call('POST', '/driver/availability', { online: true }, D)).s === 200, 'haydovchi ishga chiqdi');

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

  console.log(fails ? `\n${fails} ta xato` : '\nHammasi o\'tdi');
  process.exit(fails ? 1 : 0);
})();
