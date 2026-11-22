// Tekshiruv talablari (REQUIRE_VERIFIED standart yoqilgan) va bir raqam - ikki rol: node test-verify.js
const B = process.env.BASE || 'http://localhost:3998';
const ADMIN = process.env.ADMIN_PHONE || '+998900000000';
let fails = 0;
const ok = (c, n) => { console.log((c ? 'OK   ' : 'FAIL ') + n); if (!c) fails++; };
async function call(m, p, body, tok) {
  const r = await fetch(B + p, { method: m, headers: { 'Content-Type': 'application/json', ...(tok ? { Authorization: 'Bearer ' + tok } : {}) }, body: body ? JSON.stringify(body) : undefined });
  return { s: r.status, j: await r.json().catch(() => ({})) };
}
async function login(phone, name, role, admin) {
  const a = await call('POST', '/auth/request', { phone });
  const v = await call('POST', '/auth/verify', { phone, code: a.j.devCode, name, role, admin });
  return v.j;
}
const jpg = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.alloc(3000, 7)]).toString('base64');
(async () => {
  const ph = '+998911112233';
  const P = await login(ph, 'Ali', 'passenger');
  const D = await login(ph, '', 'driver');
  ok(P.token && D.token && P.token !== D.token, 'bir raqam: yo\'lovchi va haydovchi akkaunti');
  ok(P.user.role === 'passenger' && D.user.role === 'driver' && D.user.name === 'Ali', 'rollar to\'g\'ri, ism ko\'chirildi');
  ok((await call('POST', '/driver/availability', { online: true }, D.token)).s === 403, 'tasdiqlanmagan haydovchi ishga chiqa olmaydi');

  const S = await call('POST', '/auth/switch', { role: 'passenger' }, D.token);
  ok(S.s === 200 && S.j.user.role === 'passenger', 'rolni almashtirish');
  ok((await call('GET', '/orders/mine', null, D.token)).s === 401, 'eski token bekor');
  ok((await call('POST', '/auth/switch', { role: 'passenger' }, S.j.token)).s === 400, 'o\'zi turgan rolga o\'tib bo\'lmaydi');
  const S2 = await call('POST', '/auth/switch', { role: 'driver' }, S.j.token);
  ok(S2.s === 200 && S2.j.user.role === 'driver', 'haydovchi rejimiga qaytish');
  const DT = S2.j.token;

  ok((await call('POST', '/driver/docs', { kind: 'license', data: jpg }, DT)).s === 400, 'galereyadan (src yo\'q) hujjat rad etildi');
  for (const k of ['face', 'selfie', 'license', 'techpass', 'carplate'])
    ok((await call('POST', '/driver/docs', { kind: k, data: jpg, src: 'camera' }, DT)).s === 200, 'kamera hujjati: ' + k);
  ok((await call('POST', '/driver/availability', { online: true }, DT)).s === 403, 'admin tasdiqlamaguncha ishga chiqa olmaydi');

  const A = await login(ADMIN, 'Admin', 'passenger', true);

  const list = await call('GET', '/admin/drivers?status=all', null, A.token);
  const me = (list.j.drivers || []).find(x => x.phone === ph);
  ok(!!me, 'admin haydovchini ko\'radi');
  const uid = me && (me.id || me.uid);
  for (const k of ['face', 'selfie', 'license', 'techpass', 'carplate'])
    await call('POST', `/admin/docs/${uid}/${k}/review`, { approve: true }, A.token);
  await call('POST', '/driver/plate', { plate: '01 A 123 BC' }, DT);
  const pts0 = (await call('GET', '/driver/docs', null, DT)).j;
  ok(pts0.verified === true, 'barcha majburiy hujjatlar tasdiqlandi');
  ok((await call('POST', '/driver/availability', { online: true }, DT)).s === 200, 'tasdiqlangach ishga chiqdi');

  // ixtiyoriy taksi litsenziyasi: majburiy emas, +15 ball
  const before = (await call('GET', '/driver/points', null, DT)).j;
  await call('POST', '/driver/docs', { kind: 'taxilicense', data: jpg, src: 'camera' }, DT);
  await call('POST', `/admin/docs/${uid}/taxilicense/review`, { approve: true }, A.token);
  const after = (await call('GET', '/driver/points', null, DT)).j;
  if (before.base !== undefined) ok(after.base - before.base === 15, 'taksi litsenziyasi +15 ball: ' + before.base + ' -> ' + after.base);
  else console.log('INFO ball javobi:', JSON.stringify(before).slice(0, 120));
  console.log(fails ? `\n${fails} ta xato` : '\nHammasi yaxshi');
  process.exit(fails ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
