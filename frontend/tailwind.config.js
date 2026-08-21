/**
 * Tasarim kararlari (ui-ux-designer ilkeleri):
 * - Jenerik "SaaS" estetiginden kacinildi: mor gradient yok, Inter yok, her yerde kart yok.
 * - Konsept: "blueprint" — koyu mavi-arduvaz zemin, uzerinde teknik cizim.
 *   Koyu tema secildi cunku durum renkleri (amber/yesil/kirmizi) koyu zeminde
 *   cok daha net okunuyor; bu ekranin tek isi durum degisimini gostermek.
 * - Kafka veri yolu icin amber: "akan enerji" metaforu, servis durum renkleriyle carpismiyor.
 */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        bg:      '#0F1620',  // zemin (saf siyah degil — goz yormuyor)
        surface: '#18212E',  // kutu dolgusu
        raised:  '#1C2634',
        line:    '#2A3646',  // izgara / kenarlik
        ink:     '#E8EDF4',  // ana metin  (kontrast ~15:1)
        muted:   '#8FA0B5',  // ikincil metin (kontrast ~7:1)
        idle:    '#425268',  // bosta duran servis
        bus:     '#F5A524',  // Kafka veri yolu + aktif durum
        ok:      '#2ED47A',  // basarili
        bad:     '#FF5C5C',  // hata
        data:    '#14B8A6',  // veri katmani (PostgreSQL / Redis)
      },
      fontFamily: {
        // Space Grotesk: geometrik, teknik karakterli — "SaaS" degil "muhendislik" hissi
        display: ['"Space Grotesk"', 'system-ui', 'sans-serif'],
        // JetBrains Mono: servis adlari, topic adlari, portlar icin
        mono: ['"JetBrains Mono"', 'ui-monospace', 'monospace'],
      },
      keyframes: {
        // Olay okunun "akmasi": kesikli cizgi ilerler
        flow:  { to: { strokeDashoffset: '-24' } },
        // Aktif kutunun nefes almasi — tek seferlik degil, adim suresince
        pulse: { '0%,100%': { opacity: '1' }, '50%': { opacity: '0.45' } },
        fadeUp:{ from: { opacity: '0', transform: 'translateY(4px)' }, to: { opacity: '1', transform: 'none' } },
      },
      animation: {
        flow:  'flow 700ms linear infinite',
        pulse: 'pulse 1.4s ease-in-out infinite',
        fadeUp:'fadeUp 240ms ease-out',
      },
    },
  },
  plugins: [],
};
