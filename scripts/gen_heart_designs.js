// 하트 위젯 디자인 14종의 움직임 그림 생성 (res/drawable/hd_*.xml)
//   node scripts/gen_heart_designs.js
// 규칙
//  - 모든 그림 한 장은 0.1초(또는 그 배수): AOD 화면은 1초에 30번 바뀐다.
//  - 0번 그림은 "쉬는 자세": 잠금화면 프로그램이 AOD 에서 애니메이션을 멈추면 보통 첫 장에서 멈춘다.
//  - 지금 쓰는 하트(classic)는 gen_heart_frames.js 가 따로 만든다.
const fs = require('fs'), path = require('path');
const out = path.join(__dirname, '..', 'android', 'app', 'src', 'main', 'res', 'drawable');
const HEART = 'M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z';
const SHINE = 'M5.4,8.2a2.3,1.35 0,1 0,4.6 0a2.3,1.35 0,1 0,-4.6 0z';
const STAR = 'M5,0L6,4 10,5 6,6 5,10 4,6 0,5 4,4z'; // 10×10 반짝이
const r = n => Number(n.toFixed(2));
let written = 0;

function vector(name, w, h, body, note) {
  fs.writeFileSync(path.join(out, `${name}.xml`), `<?xml version="1.0" encoding="utf-8"?>
<!-- ${note} (scripts/gen_heart_designs.js 로 생성) -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="${w}dp"
    android:height="${h}dp"
    android:viewportWidth="${w}"
    android:viewportHeight="${h}">
${body}
</vector>
`);
  written++;
}

function animList(name, frames, note) {
  // frames: [[drawableName, durationMs], ...]
  fs.writeFileSync(path.join(out, `${name}.xml`), `<?xml version="1.0" encoding="utf-8"?>
<!-- ${note} (scripts/gen_heart_designs.js 로 생성). 위젯의 ProgressBar 에 넣어 재생, 0번 = 쉬는 자세 -->
<animation-list xmlns:android="http://schemas.android.com/apk/res/android"
    android:oneshot="false">
${frames.map(([d, ms]) => `    <item android:drawable="@drawable/${d}" android:duration="${ms}" />`).join('\n')}
</animation-list>
`);
  written++;
}

/** 24×24 하트 한 개: (x,y)=왼쪽 위, size=그릴 크기 */
function heart({ x, y, size, fill, alpha = 1, stroke = null, strokeW = 0, shine = false, rot = 0 }) {
  const s = size / 24;
  const fa = alpha < 1 ? ` android:fillAlpha="${r(alpha)}"` : '';
  const st = stroke ? ` android:strokeColor="${stroke}" android:strokeWidth="${strokeW}"${alpha < 1 ? ` android:strokeAlpha="${r(alpha)}"` : ''}` : '';
  return `    <group android:translateX="${r(x)}" android:translateY="${r(y)}" android:scaleX="${r(s)}" android:scaleY="${r(s)}"${rot ? ` android:pivotX="12" android:pivotY="12" android:rotation="${rot}"` : ''}>
        <path android:fillColor="${fill}"${fa}${st} android:pathData="${HEART}" />${shine ? `
        <path android:fillColor="#C8FFFFFF"${fa} android:pathData="${SHINE}" />` : ''}
    </group>`;
}

function star({ x, y, size, alpha, color = '#FFFFFF' }) {
  const s = size / 10;
  return `    <group android:translateX="${r(x - size / 2)}" android:translateY="${r(y - size / 2)}" android:scaleX="${r(s)}" android:scaleY="${r(s)}">
        <path android:fillColor="${color}" android:fillAlpha="${r(alpha)}" android:pathData="${STAR}" />
    </group>`;
}

// ── 1. 한 하트 "콩-닥" (여러 디자인이 색만 바꿔 씀) ─────────────────────
// 0번 쉬는 자세 → 콩 → 살짝 줄고 → 닥 → 돌아와 쉰다 (12장 × 0.1초)
const BEAT = [0.82, 0.92, 0.86, 0.96, 0.88, 0.83, 0.82, 0.82, 0.81, 0.81, 0.82, 0.82];
const BEATS = {
  pink: { fill: '#FFFF7AA2', shine: true },   // 1 글래스 알약
  big: { fill: '#FFFF5C8A', shine: false },   // 2 하트 속 숫자 (반짝임이 숫자와 겹쳐서 뺌)
  soft: { fill: '#FFFF6F96', shine: true },   // 5 미니멀, 13 영문
  rose: { fill: '#FFE63B74', shine: true },   // 8 핑크 카드, 11 말풍선
  white: { fill: '#FFFFFFFF', shine: false }, // 14 일력 (빨간 띠 위)
};
for (const [k, c] of Object.entries(BEATS)) {
  const frames = BEAT.map((s, i) => {
    const size = 24 * s / 0.96;
    const name = `hd_beat_${k}_${i}`;
    vector(name, 24, 24, heart({ x: 12 - size / 2, y: 12 - size / 2, size, fill: c.fill, shine: c.shine }), `한 하트 콩닥 (${k}) ${i}`);
    return [name, 100];
  });
  animList(`hd_beat_${k}`, frames, `한 하트 콩닥 (${k})`);
}

// ── 3. 심전도: 선이 그려지고 끝에서 하트가 뛴다 (74×30) ──────────────────
{
  const LINE = 'M1,15 L18,15 L23,6 L28,24 L33,2 L38,15 L56,15';
  const n = 16, frames = [];
  for (let i = 0; i < n; i++) {
    // 0 = 다 그려진 선 + 쉬는 하트, 1~9 = 선이 새로 그려짐, 10~15 = 하트 콩닥
    const trim = i === 0 ? 1 : i <= 9 ? r(i / 9) : 1;
    const hs = i >= 10 ? [18, 21, 19, 22, 20, 18][i - 10] : 18;
    const body = `    <path android:strokeColor="#FFFF8FB1" android:strokeWidth="2.4" android:strokeLineCap="round" android:strokeLineJoin="round"
        android:trimPathEnd="${trim}" android:pathData="${LINE}" />
${heart({ x: 63 - hs / 2, y: 15 - hs / 2, size: hs, fill: '#FFFF4F86', shine: true })}`;
    vector(`hd_ecg_${i}`, 74, 30, body, `심전도 ${i}`);
    frames.push([`hd_ecg_${i}`, 100]);
  }
  animList('hd_ecg', frames, '심전도: 선이 그려지고 하트가 뛴다');
}

// ── 4. 떠오르는 하트 (100×60, 글자 뒤) ────────────────────────────────
{
  const n = 24, hearts = [
    { x: 8, size: 10, color: '#FFFF8FB1', phase: 0 },
    { x: 84, size: 12, color: '#FFFF5C8A', phase: 8 },
    { x: 20, size: 8, color: '#FFFFC2D4', phase: 16 },
    { x: 72, size: 7, color: '#FFFFC2D4', phase: 4 },
  ];
  const frames = [];
  for (let i = 0; i < n; i++) {
    const body = hearts.map(h => {
      const t = ((i + h.phase) % n) / n; // 0..1
      const y = 52 - t * 46;
      const a = t < 0.25 ? t / 0.25 : 1 - (t - 0.25) / 0.75;
      const sz = h.size * (0.6 + 0.4 * t);
      return heart({ x: h.x - sz / 2, y: y - sz / 2, size: sz, fill: h.color, alpha: Math.max(0.05, a) });
    }).join('\n');
    vector(`hd_rise_${i}`, 100, 60, body, `떠오르는 하트 ${i}`);
    frames.push([`hd_rise_${i}`, 100]);
  }
  animList('hd_rise', frames, '떠오르는 하트');
}

// ── 6. 두 하트 만나기 (52×34) ────────────────────────────────────────
{
  const n = 22, frames = [];
  for (let i = 0; i < n; i++) {
    // 0~4 떨어져 쉼, 5~10 다가감, 11~17 만나서 위로 하트 톡, 18~21 돌아감
    let d = 0;
    if (i >= 5 && i <= 10) d = (i - 4) / 6;
    else if (i >= 11 && i <= 17) d = 1;
    else if (i >= 18) d = 1 - (i - 17) / 4;
    const move = 11 * d;
    const pop = i >= 11 && i <= 17 ? [0.4, 1.25, 1.05, 1, 1, 0.8, 0.5][i - 11] : 0;
    let body = heart({ x: 3 + move, y: 14, size: 18, fill: '#FF7FB2FF', shine: true }) + '\n' +
      heart({ x: 31 - move, y: 14, size: 18, fill: '#FFFF7AA2', shine: true });
    if (pop > 0) {
      const ps = 16 * pop;
      body += '\n' + heart({ x: 26 - ps / 2, y: 7 - ps / 2, size: ps, fill: '#FFFF4F86', alpha: i >= 16 ? 0.6 : 1 });
    }
    vector(`hd_meet_${i}`, 52, 34, body, `두 하트 만나기 ${i}`);
    frames.push([`hd_meet_${i}`, 100]);
  }
  animList('hd_meet', frames, '두 하트 만나기');
}

// ── 7. 채워지는 하트: 테두리 + 아래부터 채운 하트 (진행 막대) + 반짝이 ──────
vector('hd_fill_bg', 24, 24, `    <path android:fillColor="#33FFFFFF" android:strokeColor="#FFFFFFFF" android:strokeWidth="1.4" android:pathData="${HEART}" />`, '채워지는 하트: 빈 하트');
vector('hd_fill_fg', 24, 24, `    <path android:fillColor="#FFFF5C8A" android:pathData="${HEART}" />
    <path android:fillColor="#C8FFFFFF" android:pathData="${SHINE}" />`, '채워지는 하트: 찬 하트');
fs.writeFileSync(path.join(out, 'hd_fill_progress.xml'), `<?xml version="1.0" encoding="utf-8"?>
<!-- 채워지는 하트: 다음 100일까지 온 만큼 아래부터 채운다 (진행 막대) (scripts/gen_heart_designs.js 로 생성) -->
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:id="@android:id/background" android:drawable="@drawable/hd_fill_bg" />
    <item android:id="@android:id/progress">
        <clip android:clipOrientation="vertical" android:gravity="bottom" android:drawable="@drawable/hd_fill_fg" />
    </item>
</layer-list>
`);
written++;
{
  const A = [0.9, 0.5, 0.15, 0.3, 0.7, 1, 1, 0.9, 0.9, 0.9, 0.9, 0.9];
  const frames = A.map((a, i) => {
    vector(`hd_twinkle_${i}`, 12, 12, star({ x: 6, y: 6, size: 6 + 5 * a, alpha: a }), `반짝 ${i}`);
    return [`hd_twinkle_${i}`, 100];
  });
  animList('hd_twinkle', frames, '반짝이 하나');
}

// ── 8. 핑크 카드 반짝이 (카드 위 100×56) ───────────────────────────────
{
  const n = 16, frames = [];
  for (let i = 0; i < n; i++) {
    const a1 = 0.55 + 0.45 * Math.cos((i / n) * 2 * Math.PI);
    const a2 = 0.55 + 0.45 * Math.cos((i / n + 0.5) * 2 * Math.PI);
    const body = [star({ x: 5, y: 5, size: 6 + 5 * a1, alpha: a1 }), star({ x: 95, y: 51, size: 5 + 4 * a2, alpha: a2 }), star({ x: 88, y: 6, size: 3 + 2 * a2, alpha: a2 * 0.8 })].join('\n');
    vector(`hd_sparkle_${i}`, 100, 56, body, `핑크 카드 반짝이 ${i}`);
    frames.push([`hd_sparkle_${i}`, 100]);
  }
  animList('hd_sparkle', frames, '핑크 카드 반짝이');
}

// ── 9. 네온사인: 빛나는 테두리 하트가 가끔 깜빡 ───────────────────────────
{
  const glow = (a) => [
    `    <path android:strokeColor="#FFFF3D8B" android:strokeWidth="4.6" android:strokeAlpha="${r(0.16 * a)}" android:pathData="${HEART}" />`,
    `    <path android:strokeColor="#FFFF3D8B" android:strokeWidth="3.0" android:strokeAlpha="${r(0.32 * a)}" android:pathData="${HEART}" />`,
    `    <path android:strokeColor="#FFFFD1E3" android:strokeWidth="1.5" android:strokeAlpha="${r(Math.max(0.35, a))}" android:pathData="${HEART}" />`,
  ].join('\n');
  // 그림 크기 28×28, 하트는 가운데 24 (빛 번짐 여유)
  const wrap = (a) => `    <group android:translateX="2" android:translateY="2">\n${glow(a)}\n    </group>`;
  vector('hd_neon_on', 28, 28, wrap(1), '네온 켜짐');
  vector('hd_neon_mid', 28, 28, wrap(0.75), '네온 살짝 어두움');
  vector('hd_neon_low', 28, 28, wrap(0.3), '네온 깜빡');
  const seq = [];
  for (let i = 0; i < 30; i++) seq.push([i === 15 || i === 17 ? 'hd_neon_low' : i === 16 || i === 22 ? 'hd_neon_mid' : 'hd_neon_on', 100]);
  animList('hd_neon', seq, '네온사인 깜빡');
}

// ── 10. 궤도 하트: 점선 원을 따라 도는 하트 (100×100) ─────────────────────
{
  const C = 50, R = 41, dashes = [];
  for (let k = 0; k < 40; k += 2) {
    const a0 = (k / 40) * 2 * Math.PI, a1 = ((k + 1) / 40) * 2 * Math.PI;
    dashes.push(`M${r(C + R * Math.sin(a0))},${r(C - R * Math.cos(a0))} A${R},${R} 0 0,1 ${r(C + R * Math.sin(a1))},${r(C - R * Math.cos(a1))}`);
  }
  const ring = `    <path android:strokeColor="#FFFFFFFF" android:strokeAlpha="0.5" android:strokeWidth="1" android:pathData="${dashes.join(' ')}" />`;
  const n = 30, frames = [];
  for (let i = 0; i < n; i++) {
    const a = (i / n) * 2 * Math.PI; // 0 = 맨 위, 시계 방향
    const hx = C + R * Math.sin(a), hy = C - R * Math.cos(a), hs = 16;
    vector(`hd_orbit_${i}`, 100, 100, `${ring}\n${heart({ x: hx - hs / 2, y: hy - hs / 2, size: hs, fill: '#FFFF5C8A', stroke: '#FFFFFFFF', strokeW: 1.2, shine: true })}`, `궤도 하트 ${i}`);
    frames.push([`hd_orbit_${i}`, 133]);
  }
  animList('hd_orbit', frames, '궤도 하트 (30장 × 0.133초 = 4초 한 바퀴)');
}

// ── 11. 말풍선 꼬리 ─────────────────────────────────────────────────
vector('hd_bubble_tail', 12, 8, '    <path android:fillColor="#FFFFFFFF" android:pathData="M0,0 L12,0 L2,8 z" />', '말풍선 꼬리');

// ── 12. 픽셀 하트 (9×8 칸) ──────────────────────────────────────────
{
  const rows = ['.XX.XX.', 'XXXXXXX', 'XXXXXXX', '.XXXXX.', '..XXX..', '...X...'];
  const px = (scale) => {
    const w = 7 * scale, h = 6 * scale, ox = (9 - w) / 2, oy = (8 - h) / 2;
    const cells = [];
    rows.forEach((row, y) => [...row].forEach((c, x) => { if (c === 'X') cells.push(`M${r(ox + x * scale)},${r(oy + y * scale)}h${r(scale)}v${r(scale)}h${r(-scale)}z`); }));
    return `    <path android:fillColor="#FFFF4F6D" android:pathData="${cells.join(' ')}" />
    <path android:fillColor="#FFFFC2CC" android:pathData="M${r(ox + scale)},${r(oy + scale)}h${r(scale)}v${r(scale)}h${r(-scale)}z" />`;
  };
  vector('hd_pixel_0', 9, 8, px(1.0), '픽셀 하트 보통');
  vector('hd_pixel_1', 9, 8, px(1.15), '픽셀 하트 큼');
  animList('hd_pixel', [['hd_pixel_0', 300], ['hd_pixel_1', 200], ['hd_pixel_0', 100], ['hd_pixel_1', 200], ['hd_pixel_0', 400]], '픽셀 하트 두근 (계단식)');
}

console.log('written', written);
