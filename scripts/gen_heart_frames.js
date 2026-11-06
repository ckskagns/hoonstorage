// 하트 위젯 프레임 생성: 두 하트가 쉬지 않고 번갈아 "콩-닥" 뛴다 (12장 × 0.1초)
const fs = require('fs'), path = require('path');
const out = 'D:/APP_TEST/android/app/src/main/res/drawable';
const PATH = 'M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z';
const X = { L: -2, R: 7.5 };
const beat = [0.80, 0.93, 0.85, 0.97, 0.88, 0.82]; // 뛰는 하트: 콩(커짐)-살짝 줄고-닥(더 커짐)-돌아옴
const rest = [0.76, 0.73, 0.71, 0.70, 0.72, 0.75]; // 뒤 하트: 앞 하트가 뛸 때 살짝 물러남
const f = n => Number(n.toFixed(2)).toString();
const group = (x, s, ty, front) => `    <group android:pivotX="12" android:pivotY="12" android:translateX="${x}" android:translateY="${f(ty)}"
        android:scaleX="${f(s)}" android:scaleY="${f(s)}">
        <path android:fillColor="@color/${front ? 'heart_red' : 'heart_red_light'}"${front ? ' android:strokeColor="@color/heart_outline" android:strokeWidth="1.2"' : ''} android:pathData="${PATH}" />
    </group>`;
const frames = [];
for (let i = 0; i < 12; i++) {
  const k = i % 6, front = i < 6 ? 'L' : 'R', back = i < 6 ? 'R' : 'L';
  const fs_ = beat[k], bs = rest[k];
  const xml = `<?xml version="1.0" encoding="utf-8"?>
<!-- 겹친 미니 하트 두 개 프레임 ${i}: ${front === 'L' ? '왼쪽' : '오른쪽'} 하트가 앞에서 뛴다 (gen_heart.js 로 생성) -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="30dp"
    android:height="24dp"
    android:viewportWidth="30"
    android:viewportHeight="24">
${group(X[back], bs, 0.5 + (0.76 - bs) * 6, false)}
${group(X[front], fs_, -(fs_ - 0.72) * 5, true)}
</vector>
`;
  fs.writeFileSync(path.join(out, `heart_beat_${i}.xml`), xml);
  frames.push(`    <item android:drawable="@drawable/heart_beat_${i}" android:duration="100" />`);
}
fs.writeFileSync(path.join(out, 'heart_beat_anim.xml'), `<?xml version="1.0" encoding="utf-8"?>
<!--
  미니 하트 두 개가 쉬지 않고 번갈아 "콩-닥" 뛰는 애니메이션 (12장 × 0.1초 = 1.2초 한 바퀴).
  위젯의 ProgressBar 에 넣어 재생한다.
  - 꺼진 화면 시계(AOD)는 1초에 30번만 화면을 바꾼다. 그림 한 장을 0.1초(화면 3번)로 고르게 맞춰
    AOD 에서도 박자가 들쭉날쭉하지 않다. 멈춰 있는 구간이 없어 AOD 에서도 계속 뛰는 것처럼 보인다.
-->
<animation-list xmlns:android="http://schemas.android.com/apk/res/android"
    android:oneshot="false">
${frames.join('\n')}
</animation-list>
`);
console.log('ok', frames.length);
