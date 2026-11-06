// 하트 위젯 프레임 생성: 서로 살짝 기댄 두 하트가 쉬지 않고 번갈아 "콩-닥" 뛴다 (12장 × 0.1초)
//   node scripts/gen_heart_frames.js            → res/drawable/heart_beat_*.xml 생성
//   node scripts/gen_heart_frames.js --json out → 미리보기용 프레임 값만 JSON 으로
// 0번 그림은 "쉬는 자세": 잠금화면 프로그램이 AOD 에서 애니메이션을 멈추면 보통 첫 장으로 돌아가 멈춘다.
const fs = require('fs'), path = require('path');
const out = path.join(__dirname, '..', 'android', 'app', 'src', 'main', 'res', 'drawable');
const HEART = 'M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z';
// 앞 하트 왼쪽 위의 반짝임 (타원)
const SHINE = 'M5.4,8.2a2.3,1.35 0,1 0,4.6 0a2.3,1.35 0,1 0,-4.6 0z';
const X = { L: -1.4, R: 6.8 };
const TILT = { L: 7, R: -7 }; // 위쪽이 서로를 향해 기댄다
const beat = [0.82, 0.91, 0.85, 0.94, 0.87, 0.84]; // 뛰는 하트: 쉼 → 콩 → 살짝 줄고 → 닥 → 돌아옴
const rest = [0.76, 0.73, 0.71, 0.70, 0.72, 0.75]; // 뒤 하트: 앞 하트가 뛸 때 살짝 물러남
const r2 = n => Number(n.toFixed(2));

function frame(i) {
  const k = i % 6, front = i < 6 ? 'L' : 'R', back = i < 6 ? 'R' : 'L';
  return {
    back: { x: X[back], y: r2(0.6 + (0.76 - rest[k]) * 6), s: rest[k], rot: TILT[back] },
    front: { x: X[front], y: r2(0.6 - (beat[k] - 0.72) * 4), s: beat[k], rot: TILT[front] },
    frontSide: front,
  };
}
const frames = Array.from({ length: 12 }, (_, i) => frame(i));

if (process.argv[2] === '--json') {
  fs.writeFileSync(process.argv[3], JSON.stringify({ HEART, SHINE, frames }));
  process.exit(0);
}

const group = (g, front) => `    <group android:pivotX="12" android:pivotY="12" android:translateX="${g.x}" android:translateY="${g.y}"
        android:rotation="${g.rot}" android:scaleX="${g.s}" android:scaleY="${g.s}">
        <path android:fillColor="@color/${front ? 'heart_red' : 'heart_red_light'}" android:strokeColor="${front ? '@color/heart_outline' : '#B3FFFFFF'}" android:strokeWidth="${front ? 1.2 : 0.8}" android:pathData="${HEART}" />${front ? `
        <path android:fillColor="#C8FFFFFF" android:pathData="${SHINE}" />` : ''}
    </group>`;

const items = frames.map((f, i) => {
  fs.writeFileSync(path.join(out, `heart_beat_${i}.xml`), `<?xml version="1.0" encoding="utf-8"?>
<!-- 기댄 미니 하트 두 개 프레임 ${i}${i === 0 ? ' (쉬는 자세: AOD 에서 멈추면 보통 이 모양)' : ''}: ${f.frontSide === 'L' ? '왼쪽' : '오른쪽'} 하트가 앞에서 뛴다. scripts/gen_heart_frames.js 로 생성 -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="30dp"
    android:height="24dp"
    android:viewportWidth="30"
    android:viewportHeight="24">
${group(f.back, false)}
${group(f.front, true)}
</vector>
`);
  return `    <item android:drawable="@drawable/heart_beat_${i}" android:duration="100" />`;
});

fs.writeFileSync(path.join(out, 'heart_beat_anim.xml'), `<?xml version="1.0" encoding="utf-8"?>
<!--
  서로 기댄 미니 하트 두 개가 쉬지 않고 번갈아 "콩-닥" 뛰는 애니메이션 (12장 × 0.1초 = 1.2초 한 바퀴).
  위젯의 ProgressBar 에 넣어 재생한다. scripts/gen_heart_frames.js 로 생성.
  - 꺼진 화면 시계(AOD)는 1초에 30번만 화면을 바꾼다. 그림 한 장을 0.1초(화면 3번)로 고르게 맞췄다.
  - AOD 에서 잠금화면 프로그램이 애니메이션을 멈추면 보통 첫 장(0번, 쉬는 자세)으로 멈춘다.
    어느 장에서 멈춰도 두 하트가 예쁘게 겹쳐 보이도록 모든 장을 같은 자세로 만들었다.
-->
<animation-list xmlns:android="http://schemas.android.com/apk/res/android"
    android:oneshot="false">
${items.join('\n')}
</animation-list>
`);
console.log('ok', frames.length);
