// 하트 위젯 디자인 레이아웃·배경 생성 (res/layout/widget_hd_*.xml, res/drawable/hd_*_bg.xml)
//   node scripts/gen_heart_layouts.js
// 글자·그림 크기는 앱 코드(HeartDesigns.kt)가 위젯 칸 크기에 맞춰 다시 정한다. 여기 값은 기준 크기.
const fs = require('fs'), path = require('path');
const res = path.join(__dirname, '..', 'android', 'app', 'src', 'main', 'res');
const w = (p, s) => fs.writeFileSync(path.join(res, p), s);

const shape = (name, body, note) => w(`drawable/${name}.xml`, `<?xml version="1.0" encoding="utf-8"?>
<!-- ${note} (scripts/gen_heart_layouts.js 로 생성) -->
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
${body}
</shape>
`);
shape('hd_pill_bg', '    <corners android:radius="999dp" />\n    <solid android:color="#2EFFFFFF" />\n    <stroke android:width="1dp" android:color="#66FFFFFF" />', '하트 디자인 1 글래스 알약 배경');
shape('hd_card_bg', '    <corners android:radius="18dp" />\n    <solid android:color="#FFFFD9E5" />', '하트 디자인 8 핑크 카드 배경');
shape('hd_bubble_bg', '    <corners android:radius="16dp" />\n    <solid android:color="#FFFFFFFF" />', '하트 디자인 11 말풍선 배경');
shape('hd_cal_bg', '    <corners android:radius="10dp" />\n    <solid android:color="#FFFFFFFF" />', '하트 디자인 14 일력 종이');
shape('hd_cal_band', '    <corners android:topLeftRadius="10dp" android:topRightRadius="10dp" android:bottomLeftRadius="0dp" android:bottomRightRadius="0dp" />\n    <solid android:color="#FFEF4A6D" />', '하트 디자인 14 일력 빨간 띠');

const SH = 'android:shadowColor="#CC000000" android:shadowDy="1" android:shadowRadius="6"';
const tv = (id, sp, extra = '') => `<TextView android:id="@+id/${id}" android:layout_width="wrap_content" android:layout_height="wrap_content" android:includeFontPadding="false" android:maxLines="1" android:textSize="${sp}sp" ${extra} />`;
const white = (id, sp, bold = true, extra = '') => tv(id, sp, `android:textColor="#FFFFFFFF" ${bold ? 'android:textStyle="bold"' : ''} ${SH} ${extra}`);
const pb = (id, anim, wd, ht, extra = '') => `<ProgressBar android:id="@+id/${id}" android:layout_width="${wd}dp" android:layout_height="${ht}dp" android:importantForAccessibility="no" android:indeterminate="true" android:indeterminateOnly="true" android:indeterminateDrawable="@drawable/${anim}" ${extra} />`;
const root = (note, inner) => `<?xml version="1.0" encoding="utf-8"?>
<!-- ${note}. 글자·그림 크기는 HeartDesigns.kt 가 위젯 칸에 맞춰 키우고 줄인다 (scripts/gen_heart_layouts.js 로 생성) -->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/heart_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="4dp">
${inner}
</FrameLayout>
`;
const L = (name, note, inner) => w(`layout/${name}.xml`, root(note, inner));
const col = (inner, extra = '') => `<LinearLayout android:layout_width="wrap_content" android:layout_height="wrap_content" android:orientation="vertical" ${extra}>\n${inner}\n</LinearLayout>`;
const row = (inner, extra = '') => `<LinearLayout android:layout_width="wrap_content" android:layout_height="wrap_content" android:orientation="horizontal" android:gravity="center_vertical" ${extra}>\n${inner}\n</LinearLayout>`;
const frame = (inner, extra = '') => `<FrameLayout android:layout_width="wrap_content" android:layout_height="wrap_content" ${extra}>\n${inner}\n</FrameLayout>`;
const C = 'android:layout_gravity="center"';
const CH = 'android:layout_gravity="center_horizontal"';

L('widget_hd_pill', '1 글래스 알약: 반투명 알약 안에 콩닥 하트 + 디데이 + 날짜',
  row(pb('heart_anim', 'hd_beat_pink', 26, 26) + '\n' +
    col(white('heart_days', 20) + '\n' + white('heart_since', 11, false, 'android:layout_marginTop="2dp"'), 'android:layout_marginStart="8dp"'),
  `android:id="@+id/heart_box" android:background="@drawable/hd_pill_bg" android:paddingStart="12dp" android:paddingEnd="16dp" android:paddingTop="8dp" android:paddingBottom="8dp" ${C}`));

L('widget_hd_inside', '2 하트 속 숫자: 큰 하트가 뛰고 그 안에 숫자',
  col(frame(pb('heart_anim', 'hd_beat_big', 78, 78) + '\n' +
    tv('heart_days', 22, 'android:layout_gravity="center" android:paddingBottom="6dp" android:textColor="#FFFFFFFF" android:textStyle="bold"'), CH) + '\n' +
    white('heart_since', 11, true, CH), C));

L('widget_hd_ecg', '3 심전도: 그려지는 심전도 선 끝에서 하트가 뛴다',
  row(pb('heart_anim', 'hd_ecg', 74, 30) + '\n' +
    col(white('heart_days', 24) + '\n' + white('heart_since', 11, false, 'android:layout_marginTop="2dp"'), 'android:layout_marginStart="4dp"'), C));

L('widget_hd_rise', '4 떠오르는 하트: 글자 뒤로 작은 하트들이 떠오른다',
  frame(pb('heart_fx', 'hd_rise', 100, 60, C) + '\n' +
    col(white('heart_days', 32, true, CH) + '\n' + white('heart_since', 11, false, `${CH} android:layout_marginTop="3dp"`), C), C));

L('widget_hd_minimal', '5 미니멀 D+: 가는 글자 D+113 과 작은 콩닥 하트',
  col(`<LinearLayout android:layout_width="wrap_content" android:layout_height="wrap_content" android:orientation="horizontal" ${CH}>\n` +
    white('heart_days', 34, false, 'android:fontFamily="sans-serif-light" android:letterSpacing="0.05"') + '\n' +
    pb('heart_anim', 'hd_beat_soft', 13, 13, 'android:layout_marginStart="2dp"') + '\n</LinearLayout>\n' +
    white('heart_since', 11, false, `${CH} android:letterSpacing="0.25" android:layout_marginTop="4dp"`), C));

L('widget_hd_names', '6 두 하트 만나기: 두 사람 이름 사이에서 하트가 만난다',
  col(row(white('heart_me', 13) + '\n' + pb('heart_anim', 'hd_meet', 52, 34, 'android:layout_marginStart="4dp" android:layout_marginEnd="4dp"') + '\n' + white('heart_partner', 13), CH) + '\n' +
    white('heart_days', 26, true, `${CH} android:layout_marginTop="2dp"`), C));

L('widget_hd_fill', '7 채워지는 하트: 다음 100일까지 온 만큼 채워진 하트 + 반짝이',
  row(frame('<ProgressBar android:id="@+id/heart_fill" style="?android:attr/progressBarStyleHorizontal" android:layout_width="44dp" android:layout_height="44dp" android:importantForAccessibility="no" android:indeterminate="false" android:max="100" android:progressDrawable="@drawable/hd_fill_progress" />\n' +
    pb('heart_anim', 'hd_twinkle', 14, 14, 'android:layout_gravity="top|end"')) + '\n' +
    col(white('heart_days', 24) + '\n' + white('heart_since', 11, false, 'android:layout_marginTop="2dp"'), 'android:layout_marginStart="8dp"'), C));

L('widget_hd_card', '8 핑크 카드 반짝이: 분홍 카드 안 콩닥 하트, 모서리 반짝이',
  frame(row(pb('heart_anim', 'hd_beat_rose', 22, 22) + '\n' +
    col(tv('heart_label', 11, 'android:text="우리 사귄 지" android:textColor="#FF8A1F45" android:textStyle="bold"') + '\n' +
      tv('heart_days', 22, 'android:textColor="#FF8A1F45" android:textStyle="bold"'), 'android:layout_marginStart="8dp"'),
    'android:id="@+id/heart_box" android:background="@drawable/hd_card_bg" android:paddingStart="16dp" android:paddingEnd="18dp" android:paddingTop="10dp" android:paddingBottom="10dp"') + '\n' +
    pb('heart_fx', 'hd_sparkle', 100, 56, C).replace('android:layout_width="100dp" android:layout_height="56dp"', 'android:layout_width="match_parent" android:layout_height="match_parent"'), C));

const NEON = 'android:textColor="#FFFFE6F0" android:shadowColor="#FFFF3D8B" android:shadowDx="0" android:shadowDy="0" android:shadowRadius="14"';
L('widget_hd_neon', '9 네온사인: 빛나는 테두리 하트가 가끔 깜빡, 분홍 빛 글자',
  row(pb('heart_anim', 'hd_neon', 34, 34) + '\n' +
    col(tv('heart_days', 26, NEON + ' android:textStyle="bold"') + '\n' + tv('heart_since', 11, NEON + ' android:layout_marginTop="2dp"'), 'android:layout_marginStart="6dp"'), C));

L('widget_hd_orbit', '10 궤도 하트: 숫자 둘레 점선 원을 하트가 돈다',
  frame(pb('heart_fx', 'hd_orbit', 96, 96, C) + '\n' +
    col(white('heart_days', 24, true, CH) + '\n' + white('heart_since', 11, false, `${CH} android:layout_marginTop="1dp"`), C), C));

L('widget_hd_bubble', '11 말풍선: 하얀 말풍선 "오늘로 113일째" + 콩닥 하트',
  col(row(tv('heart_days', 14, 'android:textColor="#FF3A2A33" android:textStyle="bold"') + '\n' + pb('heart_anim', 'hd_beat_rose', 16, 16, 'android:layout_marginStart="5dp"'),
    'android:id="@+id/heart_box" android:background="@drawable/hd_bubble_bg" android:paddingStart="14dp" android:paddingEnd="12dp" android:paddingTop="9dp" android:paddingBottom="9dp"') + '\n' +
    '<ImageView android:id="@+id/heart_tail" android:layout_width="12dp" android:layout_height="8dp" android:layout_marginStart="18dp" android:importantForAccessibility="no" android:src="@drawable/hd_bubble_tail" />', C));

L('widget_hd_pixel', '12 픽셀 하트: 8비트 하트가 계단식으로 두근, 고정폭 글자',
  row(pb('heart_anim', 'hd_pixel', 36, 32) + '\n' +
    col(white('heart_days', 24, true, 'android:fontFamily="monospace"') + '\n' + white('heart_since', 11, false, 'android:fontFamily="monospace" android:layout_marginTop="2dp"'), 'android:layout_marginStart="10dp"'), C));

L('widget_hd_serif', '13 영문 타이포: 명조 113 days 와 작은 콩닥 하트',
  col('<LinearLayout android:layout_width="wrap_content" android:layout_height="wrap_content" android:orientation="horizontal" android:baselineAligned="true">\n' +
    white('heart_days', 38, true, 'android:fontFamily="serif"') + '\n' +
    white('heart_unit', 14, false, 'android:fontFamily="serif" android:textStyle="italic" android:text="days" android:layout_marginStart="5dp"') + '\n' +
    pb('heart_anim', 'hd_beat_soft', 14, 14, 'android:layout_marginStart="4dp" android:layout_gravity="center_vertical"') + '\n</LinearLayout>\n' +
    white('heart_since', 11, false, 'android:letterSpacing="0.12" android:layout_marginTop="3dp"'), C));

L('widget_hd_calendar', '14 일력 한 장: 빨간 띠(사귄 지 + 콩닥 하트) 아래 큰 숫자',
  col('<LinearLayout android:id="@+id/heart_band" android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:gravity="center" android:background="@drawable/hd_cal_band" android:paddingTop="3dp" android:paddingBottom="3dp">\n' +
    tv('heart_label', 11, 'android:text="사귄 지" android:textColor="#FFFFFFFF" android:textStyle="bold"') + '\n' +
    pb('heart_anim', 'hd_beat_white', 11, 11, 'android:layout_marginStart="3dp"') + '\n</LinearLayout>\n' +
    tv('heart_days', 28, `${CH} android:layout_marginTop="3dp" android:textColor="#FF3A2A33" android:textStyle="bold"`) + '\n' +
    tv('heart_since', 11, `${CH} android:layout_marginBottom="5dp" android:paddingStart="8dp" android:paddingEnd="8dp" android:textColor="#FF8A6A78"`),
  `android:id="@+id/heart_box" android:background="@drawable/hd_cal_bg" ${C}`));

console.log('ok');
