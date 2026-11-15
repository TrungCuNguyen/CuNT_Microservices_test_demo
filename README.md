# BÀI KIỂM TRA THỰC HÀNH: KIẾN TRÚC MICROSERVICES

Hệ thống Microservices được xây dựng dựa trên công nghệ **Java 17**, **Spring Boot 3.4.x**, **Spring Cloud 2024.x**, **Spring Cloud Gateway**, **Netflix Eureka**, **Spring Cloud OpenFeign**, và cơ sở dữ liệu **PostgreSQL**.

---

## I. Sơ đồ kiến trúc hệ thống (Architecture Diagram)

### 1. Sơ đồ Mermaid
```mermaid
graph TD
    Client([Client / Postman / Frontend]) -->|HTTP Requests| Gateway[API Gateway: 8088 / 8080]
    
    subgraph ServiceRegistry [Service Registry]
        Eureka[Eureka Server: 8761]
    end

    Gateway -.->|1. Tra cứu Service ID| Eureka
    Gateway -->|2. Route /api/orders/**| OrderService[Order Service: 8081]
    Gateway -->|2. Route /api/inventory/**| InventoryService[Inventory Service: 8082]

    OrderService -.->|Đăng ký & Heartbeat| Eureka
    InventoryService -.->|Đăng ký & Heartbeat| Eureka
    Gateway -.->|Đăng ký & Heartbeat| Eureka

    OrderService -->|OpenFeign: GET /api/inventory/{id}| InventoryService

    subgraph Databases [PostgreSQL Databases]
        DB_Order[(order_db: orders table)]
        DB_Inventory[(inventory_db: product table)]
    end

    OrderService --> DB_Order
    InventoryService --> DB_Inventory
```

### 2. Sơ đồ dạng văn bản (Text Diagram)
```text
                          +-------------------------------+
                          |   Client (Postman / Browser)  |
                          +---------------+---------------+
                                          |
                                          v [Mọi request qua port 8088]
                  +-----------------------------------------------+
                  |       API GATEWAY (Spring Cloud Gateway)      |
                  |     - GlobalFilter: In log (Time, Method, URI)|
                  +-------+-------------------------------+-------+
                          |                               |
        Route: /api/orders/**            Route: /api/inventory/**
                          |                               |
                          v                               v
            +-----------------------+       +---------------------------+
            |     ORDER SERVICE     |       |     INVENTORY SERVICE     |
            |     (Port: 8081)      |       |       (Port: 8082)        |
            +-----------+-----------+       +-------------+-------------+
                        |                                 |
                        |--[OpenFeign: lb://inventory]---|
                        |  (Kiểm tra tồn kho & trừ kho)   |
                        v                                 v
            +-----------------------+       +---------------------------+
            |  Database: order_db   |       |  Database: inventory_db   |
            |  Bảng: orders         |       |  Bảng: product            |
            +-----------------------+       +---------------------------+
                        ^                                 ^
                        |                                 |
                        +---------[Eureka Client]---------+
                                          |
                                          v
                          +-------------------------------+
                          |    EUREKA SERVER (Port: 8761) |
                          |       Service Registry        |
                          +-------------------------------+
```

---

## II. Cấu trúc dự án

```text
test_demo_micro/
├── .gitignore                      # Chỉ định các file cần ignore (build, .gradle, IDE files,...)
├── build.gradle                    # Root Gradle configuration
├── settings.gradle                 # Quản lý 4 module con
├── gradlew / gradlew.bat           # Gradle Wrapper
├── README.md                       # Sơ đồ kiến trúc & Câu trả lời Task 3
│
├── eureka-server/                  # [Module 1] Eureka Service Registry
│   ├── build.gradle
│   └── src/main/
│       ├── java/com/example/eurekaserver/EurekaServerApplication.java
│       └── resources/application.properties (Port 8761)
│
├── api-gateway/                    # [Module 2] Spring Cloud Gateway
│   ├── build.gradle
│   └── src/main/
│       ├── java/com/example/apigateway/
│       │   ├── ApiGatewayApplication.java
│       │   └── filter/LoggingFilter.java  (Global Filter log Time, Method, URI)
│       └── resources/application.yml (Port 8088 / routes lb://order-service, lb://inventory-service)
│
├── inventory-service/              # [Module 3] Inventory Service
│   ├── build.gradle
│   └── src/main/
│       ├── java/com/example/inventoryservice/
│       │   ├── InventoryServiceApplication.java
│       │   ├── config/DataInitializer.java (Seed dữ liệu mẫu ban đầu)
│       │   ├── controller/InventoryController.java (GET /api/inventory/{productId})
│       │   ├── dto/ProductRequest.java, ProductResponse.java
│       │   ├── entity/Product.java (Bảng product thuộc inventory_db)
│       │   ├── repository/ProductRepository.java
│       │   └── service/ (InventoryService, InventoryServiceImpl)
│       └── resources/application.properties (Port 8082, DB inventory_db)
│
└── order-service/                  # [Module 4] Order Service
    ├── build.gradle
    └── src/main/
        ├── java/com/example/orderservice/
        │   ├── OrderServiceApplication.java (@EnableFeignClients)
        │   ├── client/InventoryClient.java (OpenFeign gọi qua Service ID "inventory-service")
        │   ├── controller/OrderController.java (POST /api/orders, GET /api/orders)
        │   ├── dto/OrderRequest.java, OrderResponse.java, ProductDto.java
        │   ├── entity/Order.java (Bảng orders thuộc order_db, Không dùng FK)
        │   ├── repository/OrderRepository.java
        │   └── service/ (OrderService, OrderServiceImpl)
        └── resources/application.properties (Port 8081, DB order_db)
```

---

## III. Cơ sở dữ liệu (PostgreSQL)

### 1. Database `order_db` - Bảng `orders`
```sql
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,  -- Lưu ý: Không dùng Foreign Key vì khác Database
    quantity INT NOT NULL,
    status VARCHAR(50) NOT NULL, -- PENDING, COMPLETED, CANCELLED
    total_price NUMERIC(12, 2)
);
```

### 2. Database `inventory_db` - Bảng `product`
```sql
CREATE TABLE product (
    id BIGSERIAL PRIMARY KEY,
    product_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,       -- Số lượng hiện có trong kho
    price NUMERIC(12, 2) NOT NULL
);
```

---

## IV. Hướng dẫn khởi chạy hệ thống

### 1. Yêu cầu môi trường
- Java: JDK 17 trở lên
- PostgreSQL Server đang chạy trên cổng `5432` với tài khoản `postgres` / mật khẩu `17111998` (hoặc cấu hình lại biến môi trường `DB_PASSWORD`).
- Hai database `order_db` và `inventory_db` đã được tạo trên PostgreSQL:
  ```sql
  CREATE DATABASE order_db;
  CREATE DATABASE inventory_db;
  ```

### 2. Biên dịch toàn bộ dự án
Mở terminal tại thư mục gốc của dự án:
```powershell
./gradlew build -x test
```

### 3. Thứ tự khởi chạy 4 dịch vụ
Chạy 4 terminal độc lập theo đúng thứ tự:

1. **Khởi chạy Eureka Server:**
   ```powershell
   java -jar eureka-server/build/libs/eureka-server-0.0.1-SNAPSHOT.jar
   ```
   > Dashboard Eureka: http://localhost:8761

2. **Khởi chạy Inventory Service:**
   ```powershell
   java -jar inventory-service/build/libs/inventory-service-0.0.1-SNAPSHOT.jar
   ```
   > Cổng: 8082

3. **Khởi chạy Order Service:**
   ```powershell
   java -jar order-service/build/libs/order-service-0.0.1-SNAPSHOT.jar
   ```
   > Cổng: 8081

4. **Khởi chạy API Gateway:**
   ```powershell
   java -jar api-gateway/build/libs/api-gateway-0.0.1-SNAPSHOT.jar
   ```
   > Cổng: 8088 (Mọi request từ phía người dùng gửi tới cổng này)

---

## V. Hướng dẫn kiểm thử các API (Qua Gateway - Port 8088)

### 1. Kiểm tra Global Filter & Lấy thông tin sản phẩm (Inventory Service)
**Request:**
```http
GET http://localhost:8088/api/inventory/1
```
**cURL:**
```bash
curl -X GET http://localhost:8088/api/inventory/1
```
**Kết quả trả về:**
```json
{
  "id": 1,
  "productName": "Dell XPS 15 9530",
  "quantity": 15,
  "price": 1899.99
}
```
**Console API Gateway in log:**
```text
[GATEWAY GLOBAL FILTER] Time: 2026-11-14 11:56:34.392 | Method: GET | URI: http://localhost:8088/api/inventory/1
```

---

### 2. Đặt hàng thành công (Order Service gọi Inventory Service kiểm tra tồn kho & tính tiền)
**Request:**
```http
POST http://localhost:8088/api/orders
Content-Type: application/json

{
  "productId": 1,
  "quantity": 2
}
```
**cURL:**
```bash
curl -X POST http://localhost:8088/api/orders \
  -H "Content-Type: application/json" \
  -d '{"productId": 1, "quantity": 2}'
```
**Kết quả trả về:**
```json
{
  "id": 1,
  "productId": 1,
  "quantity": 2,
  "status": "COMPLETED",
  "totalPrice": 3799.98,
  "message": "Order completed successfully"
}
```

---

### 3. Đặt hàng thất bại do không đủ tồn kho
**Request:**
```http
POST http://localhost:8088/api/orders
Content-Type: application/json

{
  "productId": 1,
  "quantity": 100
}
```
**Kết quả trả về:**
```json
{
  "id": 2,
  "productId": 1,
  "quantity": 100,
  "status": "CANCELLED",
  "totalPrice": 0.00,
  "message": "Insufficient stock. Available: 13, Requested: 100"
}
```

---

### 4. Đặt hàng thất bại do sản phẩm không tồn tại
**Request:**
```http
POST http://localhost:8088/api/orders
Content-Type: application/json

{
  "productId": 99999,
  "quantity": 1
}
```
**Kết quả trả về:**
```json
{
  "id": 3,
  "productId": 99999,
  "quantity": 1,
  "status": "CANCELLED",
  "totalPrice": 0.00,
  "message": "Product not found with id: 99999"
}
```

---

### 5. Lấy danh sách toàn bộ đơn hàng
**Request:**
```http
GET http://localhost:8088/api/orders
```
**Kết quả trả về:** Danh sách các đơn hàng với các trạng thái `COMPLETED`, `CANCELLED` và `total_price` tương ứng.

---

## VI. Task 3: Phân tích kiến trúc (Architecture Analysis)

### Câu 1: Giải thích cơ chế "Service Discovery": Tại sao Gateway không nên gọi trực tiếp địa chỉ IP/Port của Service?

#### 1. Cơ chế Service Discovery (Cơ chế phát hiện dịch vụ)
- Trong kiến trúc Microservices phân tán, các instance của một service thường được triển khai trên môi trường cloud/container (Docker, Kubernetes) với địa chỉ IP và Port được cấp phát động (dynamic IP/Port), có thể thay đổi bất cứ lúc nào khi scale up, scale down, deploy bản mới hoặc khi một container bị lỗi và khởi động lại trên node khác.
- **Service Discovery** (như Netflix Eureka Server) đóng vai trò là một **danh bạ trung tâm (Registry)**:
  1. **Đăng ký dịch vụ (Service Registration):** Khi mỗi service (Order Service, Inventory Service) khởi động, nó tự động gửi thông tin (Service ID, IP, Port, metadata) lên Eureka Server và định kỳ gửi tín hiệu kiểm tra tình trạng sống (Heartbeat - ví dụ mỗi 30 giây).
  2. **Tra cứu dịch vụ (Service Lookup/Resolution):** Khi API Gateway hoặc một service khác cần gửi request, thay vì lưu cứng IP/Port, nó tra cứu Eureka theo tên định danh logic (**Service ID**, ví dụ `lb://order-service`). Eureka trả về danh sách các node đang khỏe mạnh (`status: UP`). Client-side load balancer (như Spring Cloud LoadBalancer) sẽ chọn một node tối ưu theo thuật toán (Round Robin, Least Connection) để gửi request.

#### 2. Tại sao Gateway KHÔNG NÊN gọi trực tiếp địa chỉ IP/Port của Service?
- **Hard-coded Tight Coupling (Ràng buộc chặt):** Nếu cấu hình cứng IP/Port trong Gateway (ví dụ `http://192.168.1.10:8081`), mỗi khi service đổi server, đổi port hoặc triển khai instance mới, người quản trị bắt buộc phải sửa cấu hình file của Gateway và khởi động lại Gateway, gây gián đoạn toàn bộ hệ thống (downtime).
- **Mất khả năng tự động cân bằng tải (Load Balancing):** Khi có nhiều instance cùng phục vụ một service, việc gọi trực tiếp IP khiến Gateway chỉ gửi request vào một máy duy nhất, không thể phân tải đều.
- **Không có cơ chế tự phục hồi (Fault Tolerance / Dynamic Health Check):** Khi một instance chết hoặc crash, Gateway nếu gọi trực tiếp IP sẽ liên tục bị timeout hoặc 500/502. Với Service Discovery, Eureka sẽ nhanh chóng gỡ bỏ (deregister/evict) instance lỗi ra khỏi danh bạ, Gateway tự động chuyển hướng request sang các instance còn sống mà không có lỗi.
- **Khó khăn khi Autoscaling:** Trong môi trường Kubernetes/Cloud, số lượng instance có thể tự động tăng từ 2 lên 20 pod khi lưu lượng cao và giảm về 2 khi thấp. Gateway không thể cấu hình tĩnh theo kịp tốc độ scale này.

---

### Câu 2: Nếu số lượng request tăng đột biến, bạn sẽ làm gì để mở rộng (Scale) Order Service mà không làm thay đổi cấu hình của Gateway?

Để mở rộng Order Service phục vụ lượng request tăng đột biến mà **không làm thay đổi bất kỳ dòng cấu hình nào của API Gateway**, ta thực hiện các giải pháp sau:

#### 1. Mở rộng theo chiều ngang (Horizontal Scaling / Scale-Out)
- Khởi chạy thêm nhiều instance mới của `order-service` với các port ngẫu nhiên hoặc port khác nhau:
  ```powershell
  # Instance 1
  java -jar order-service.jar --server.port=8081
  # Instance 2
  java -jar order-service.jar --server.port=8083
  # Instance 3 (hoặc port ngẫu nhiên)
  java -jar order-service.jar --server.port=0
  ```
- Hoặc trong môi trường Docker Swarm / Kubernetes:
  ```bash
  kubectl scale deployment order-service --replicas=5
  ```
- **Tại sao Gateway không cần đổi cấu hình?**
  - Mọi instance mới khởi động đều mang cùng một `spring.application.name=order-service`.
  - Các instance này tự động đăng ký danh bạ vào Eureka Server.
  - Gateway định tuyến thông qua địa chỉ logic `uri: lb://order-service`.
  - Spring Cloud LoadBalancer tích hợp trong Gateway tự động cập nhật danh sách instance từ Eureka và phân phối request (Round Robin) đến tất cả các instance đang chạy một cách hoàn toàn trong suốt.

#### 2. Tối ưu Connection Pool & Database Scaling
- Mở rộng số lượng connection trong HikariCP của Order Service để chịu tải đồng thời.
- Áp dụng kỹ thuật Read/Write Replica cho cơ sở dữ liệu `order_db` (Master xử lý ghi đơn hàng, Read Replicas phục vụ các API truy vấn).

#### 3. Sử dụng Cache (Redis)
- Đưa thông tin sản phẩm phổ biến và kết quả kiểm tra vào Redis Cache để giảm áp lực gọi sang Inventory Service.

#### 4. Áp dụng Rate Limiting & Circuit Breaker tại Gateway
- Cấu hình Redis RateLimiter trên Gateway để giới hạn số request/giây từ mỗi client, bảo vệ Order Service khỏi tình trạng quá tải (DoS).
- Sử dụng Resilience4j Circuit Breaker để ngắt mạch nhanh chóng khi hệ thống bị chậm, tránh lỗi dây chuyền (Cascading Failure).

---

### Câu 3: So sánh ưu/nhược điểm của phương thức giao tiếp qua OpenFeign (Đồng bộ) so với Kafka (Bất đồng bộ) trong bài toán này

| Tiêu chí | OpenFeign (Đồng bộ - Synchronous HTTP) | Apache Kafka (Bất đồng bộ - Asynchronous Event-Driven) |
| :--- | :--- | :--- |
| **Bản chất giao tiếp** | Request - Response qua HTTP/REST API. Client gửi request và **phải chờ (blocking)** kết quả trả về từ Server. | Event-Driven qua Message Broker. Producer bắn sự kiện (event) vào Topic và lập tức kết thúc lượt xử lý mà **không phải chờ**. Consumer tự pull và xử lý sau. |
| **Ưu điểm** | - **Đơn giản, trực quan, dễ debug:** Dễ viết code (`@FeignClient`), luồng xử lý tuần tự tương tự gọi hàm cục bộ.<br>- **Phản hồi tức thì cho người dùng:** Khách hàng biết ngay lập tức đơn hàng đã thành công, hết hàng hay mã lỗi chi tiết.<br>- **Kiểm tra tính nhất quán dữ liệu tại thời điểm thực:** Dữ liệu tồn kho được đọc trực tiếp từ database của Inventory Service tại thời điểm đặt hàng.<br>- **Chi phí hạ tầng thấp:** Không cần cài đặt và vận hành hệ thống message broker phức tạp. | - **Loose Coupling (Độc lập tối đa):** Order Service và Inventory Service hoàn toàn tách rời, không cần biết service kia ở đâu hay có đang online không.<br>- **Hiệu năng và thông lượng cực cao:** Không bị nghẽn I/O chờ network, Order Service có thể nhận hàng chục nghìn đơn hàng/giây và đẩy vào Kafka Topic.<br>- **Khả năng chịu lỗi vượt trội (Fault Tolerance):** Nếu Inventory Service tạm thời bị sập hoặc bảo trì, tin nhắn vẫn được lưu an toàn trên Kafka Broker. Khi service sống lại sẽ tiếp tục xử lý mà không làm mất đơn.<br>- **Dễ mở rộng tính năng mới:** Nhiều consumer khác (Notification Service gửi email, Analytics Service, Recommendation) có thể cùng subscribe topic `order-events` mà không cần sửa code Order Service. |
| **Nhược điểm** | - **Tight Coupling (Phụ thuộc sẵn sàng):** Nếu Inventory Service bị chậm hoặc chết, Order Service sẽ bị block thread, dẫn đến lỗi hàng loạt và có nguy cơ sập dây chuyền (Cascading Failure).<br>- **Thông lượng bị giới hạn:** Thời gian hoàn tất request bằng tổng thời gian của Order Service + Inventory Service + độ trễ mạng.<br>- **Khó khăn khi scale chịu tải đột biến:** Lượng request lớn dồn dập sẽ đánh sập cả 2 service cùng lúc. | - **Phức tạp về mặt kiến trúc & debug:** Khó theo dõi luồng dữ liệu (cần Distributed Tracing như Zipkin/Micrometer), khó debug lỗi giao dịch.<br>- **Tính nhất quán sau (Eventual Consistency):** Khách hàng không nhận được kết quả cuối cùng ngay lập tức, cần cơ chế thông báo trạng thái đơn hàng (WebSocket / SSE / Polling).<br>- **Phải xử lý bài toán Transaction phân tán (Saga Pattern):** Nếu trừ kho thất bại, phải phát event bù trừ (Compensating Transaction) để hoàn lại tiền hoặc hủy đơn.<br>- **Chi phí tài nguyên & vận hành:** Cần cài đặt, cấu hình cluster ZooKeeper/KRaft, phân vùng (partitions), quản lý offset, xử lý tin nhắn trùng (Idempotency). |
| **Áp dụng phù hợp cho bài toán** | Phù hợp với hệ thống quy mô vừa và nhỏ, luồng nghiệp vụ đơn giản cần trả về kết quả thành công/thất bại ngay lập tức cho client. | Phù hợp với các sàn thương mại điện tử lớn (Shopee, Tiki, Amazon), các đợt Flash Sale lưu lượng cực lớn, hệ thống có nhiều service vệ tinh (thanh toán, kho, vận chuyển, email). |

