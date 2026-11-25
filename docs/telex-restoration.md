# Khôi phục Telex sau khi hủy dấu

## Lỗi đã xác nhận

`cusstom` đang hiển thị `custom`, nhưng `Session.commit()` thấy từ không hợp lệ
và trả lại `rawBuf` (`cusstom`). Cùng đường lỗi xuất hiện với `tesst`, `texxt`,
`porrt`, `sofft`, `lefft`, `wweb`, `rooot`, `boook`, `gooogle` và `deeep`.

Điều tra thêm xác nhận hai trường hợp liên quan:

- Sau `ww`, engine vẫn tìm âm tiết mới và có thể biến `wword` thành `wỏd`,
  `wwifi` thành `wìi`. Cờ `isUndo` cũ còn ngăn việc khôi phục trong lúc gõ.
- Backspace dựng lại `rawBuf` từ tất cả phép biến đổi còn trong đồ thị.
  Các phím dấu đã hủy vẫn có thể xuất hiện lại khi kết thúc từ hoặc gõ tiếp.

Không phải mọi phím lặp trong tài liệu mô tả đều là lệnh hủy dấu trong engine
hiện tại: `form`, `sleep` đã được nhận là chữ nguyên văn trước vị trí đó.
`forrm`, `sleeep` vì vậy phải được giữ nguyên; không được tự xóa ký tự lặp.

## Lựa chọn xử lý

Chỉ giữ nguyên ASCII lúc nhấn cách sẽ sửa `custom`, nhưng làm mất chữ lặp thật
trong `issue`, `error`, `access`, `success`, `coffee`, `buffer`. Xóa mọi ký tự
lặp trong bộ đệm cũng không phân biệt được chữ thật với lệnh sửa.

Bản sửa dùng danh tính node và loại phép biến đổi trong đồ thị hiện có:

1. Dựng `restorationKeys()` bằng cách bỏ các phép đặt dấu đã bị `isUndo` hủy
   trên cùng ký tự gốc. Giữ các phím chữ, các dấu chưa hủy và chữ hoa/thường.
   Cách này cũng xử lý `ww`: phím `w` thứ hai là lệnh, không phải chữ mới.
2. Sau khi hủy sạch dấu ở một từ đã sai cấu trúc tiếng Việt, nhận phần tiếp
   theo dưới dạng chữ nguyên văn, tránh đặt dấu lại vào đuôi từ.
3. Dùng chuỗi khôi phục này ở cả nhánh tự khôi phục khi gõ và khi kết thúc từ.
4. Backspace xóa theo chữ hiển thị và bỏ các phím dấu đã hủy khỏi bộ đệm được
   dựng lại, tránh phục hồi ký tự đã xóa.
5. Khi kết thúc từ, chỉ lấy lại phím vật lý nếu chúng tạo thành một từ có trong
   từ điển tiếng Anh, còn phương án đã loại lệnh hủy dấu không có trong từ điển.
   IME dùng `assets/dict/en.txt` có sẵn, nạp một lần và tra khớp chính xác.
   Engine thuần nhận hàm tra cứu qua tham số của `commit()`.

Không thay đổi quy tắc đặt dấu, ma trận âm tiết, gõ dấu muộn hoặc chính sách
hủy một loại dấu trong khi vẫn giữ loại dấu khác (`tooss → tôs`).

## Trường hợp mơ hồ

Không thể xác định tuyệt đối ý định từ phím vật lý. `off` có thể là từ `off`
hoặc người dùng hủy dấu để lấy `of`; `ass` có thể là từ `ass` hoặc lệnh để lấy
`as`. Khi cả hai là từ Anh hợp lệ, ưu tiên kết quả đang hiển thị sau hủy dấu.
Tên riêng hoặc thuật ngữ ngoài từ điển cũng ưu tiên kết quả đã loại lệnh hủy.
Người dùng có thể gõ thêm ký tự lặp thật sau khi hủy dấu hoặc chọn layout Anh.

## Kiểm chứng

`TelexRestoreTest` kiểm tra các nhóm: hủy dấu giữa/cuối từ, chữ lặp thật,
không có từ điển, Backspace rồi gõ tiếp, chữ hoa, ưu tiên khi mơ hồ, từ nguyên
văn, áp dụng diff qua Space/Enter/dấu câu/Tab, giữ nguyên dấu tiếng Việt,
locale và reset giữa các từ. Các test kết thúc từ cũ cũng dùng cùng từ điển
Anh của IME để kiểm tra việc phân biệt chữ lặp.

Kết quả: toàn bộ 90 test trong 17 suite đều qua, gồm 33 test Telex (12 test
mới về khôi phục). `git diff --check` không phát hiện lỗi khoảng trắng.

Lệnh chạy toàn bộ unit test trên Windows:

```powershell
$env:JAVA_HOME = 'C:\Users\huann\.jdks\jbr-21.0.11'
$env:ANDROID_HOME = 'D:\Android\Sdk'
.\gradlew.bat :app:testDebugUnitTest --no-configuration-cache --console=plain
```

Chưa kiểm thử trực tiếp trên điện thoại; test ranh giới từ mô phỏng cách IME
áp dụng `Telex.diff()` và gọi `commit()` rồi `reset()`.
