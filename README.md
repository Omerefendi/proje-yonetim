# Proje Yonetim Sistemi

Bu projede backend Spring Boot, frontend ise statik HTML/CSS/JS olarak calisir.

## Calisan Adresler

- Frontend: http://127.0.0.1:5500
- Backend API: http://127.0.0.1:8080/api
- H2 Console: http://127.0.0.1:8080/h2-console

## Varsayilan Giris Bilgileri

- Admin: `admin` / `admin123`
- Yonetici: `manager` / `manager123`
- Kullanici: `ahmet` / `ahmet123`

## Nasil Calistirilir

Iki ayri terminal ac.

### 1. Backend

```powershell
cd backend
mvn spring-boot:run
```

Backend varsayilan olarak `8080` portunda calisir.

### 2. Frontend

```powershell
cd frontend
python -m http.server 5500
```

Frontend `5500` portunda acilir. Tarayicidan `http://127.0.0.1:5500/login.html` adresine gidebilirsin.

## Proje Dokumanlari

- Her proje icin ayri dokuman yukleyebilirsin.
- Desteklenen uzantilar: `pdf`, `doc`, `docx`, `xls`, `xlsx`, `jpg`, `jpeg`, `png`
- Yuklenen dosyalar backend tarafinda `backend/data/project-documents/` altinda tutulur.

## Notlar

- Frontend API olarak otomatik sekilde `http://localhost:8080/api` adresini bulur.
- Ilk acilista backend veritabani olusturur ve varsayilan kullanicilari ekler.
- Uygulamalari terminalden calistirirken durdurmak icin ilgili terminalde `Ctrl + C` kullan.
