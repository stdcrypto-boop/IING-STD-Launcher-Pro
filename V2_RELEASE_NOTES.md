# IING STD Launcher Pro V2 — Release candidate

Versi ini disiapkan untuk pengujian akhir pada perangkat Android. Build GitHub Actions yang hijau menunjukkan kompilasi berhasil, bukan jaminan semua fungsi runtime sudah lolos.

## Fitur yang tersedia dalam kode
- Beranda dengan ikon aplikasi asli, halaman, penataan ulang ikon, dan pilihan hapus banyak pintasan (tidak uninstall aplikasi).
- Daftar aplikasi dan pemilih aplikasi beranda/dock dengan logo asli serta pencarian.
- Folder dengan penggabungan ikon, penggantian nama, dan penghapusan folder yang mengembalikan anggota ke beranda.
- Dock 4–6 slot dengan bingkai emas dan ukuran ikon yang dapat diubah.
- Panel cepat dengan tombol burgundy bulat dan pengaturan silver ringkas.
- Jam berdetik dengan ilustrasi ayam menghadap depan, foto hati dan tanda tangan yang dapat diatur.
- Nama operator seluler otomatis sebagai pengganti batang sinyal kustom yang tidak sinkron.
- Bohlam putih dengan persentase baterai yang diperbarui dari status Android.
- Ekspor/impor pengaturan tata letak.

## Pengujian sebelum dinyatakan stabil
1. Pasang APK dari workflow paling baru yang hijau, lalu buka launcher.
2. Coba tambah/pilih/hapus banyak ikon beranda dan pastikan aplikasi tidak terhapus dari HP.
3. Coba folder, dock, pencarian, geser halaman, dan buka aplikasi.
4. Periksa operator, bohlam baterai, jam, foto, dan panel.
5. Restart launcher dan pastikan pengaturan tetap tersimpan.

## Batasan
- Tombol panel membuka halaman Pengaturan Android, bukan mengubah Quick Settings sistem secara langsung.
- Nama operator mengikuti informasi yang disediakan Android dan mungkin berbeda pada SIM ganda/roaming.
- Fitur lanjutan seperti tema AMOLED/kustom, kunci tata letak, serta gestur tambahan belum dinyatakan selesai.
- Pengujian langsung pada HP diperlukan sebelum label final/stabil.
