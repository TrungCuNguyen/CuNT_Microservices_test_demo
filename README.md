# BÀI KIỂM TRA THỰC HÀNH: KIẾN TRÚC MICROSERVICES

Hệ thống Microservices được xây dựng dựa trên công nghệ **Java 17**, **Spring Boot 3.4.x**, **Spring Cloud 2024.x**, **Spring Cloud Gateway**, **Netflix Eureka**, **Spring for Apache Kafka (Cách B - Event-Driven Architecture)**, và cơ sở dữ liệu **PostgreSQL**.

---

## I. Sơ đồ kiến trúc hệ thống (Architecture Diagram - Kafka Cách B)

### 1. Sơ đồ Mermaid
```mermaid
graph TD
    Client([Client / Postman / Frontend]) -->|HTTP Requests| Gateway[API Gateway: 8088]
    
    subgraph ServiceRegistry [Service Registry]
        Eureka[Eureka Server: 8761]
    end

    Gateway -.->|1. Tra cứu Service ID| Eureka
    Gateway -->|2. Route /api/orders/**| OrderService[Order Service: 8081]
    Gateway -->|2. Route /api/inventory/**| InventoryService[Inventory Service: 8082]

    OrderService -.->|Đăng ký & Heartbeat| Eureka
    InventoryService -.->|Đăng ký & Heartbeat| Eureka
    Gateway -.->|Đăng ký & Heartbeat| Eureka

    subgraph KafkaBroker [Apache Kafka Broker: 9092]
        TopicOrderEvents[Topic: order-events]
        TopicInventoryResponse[Topic: inventory-response-events]
    end

    OrderService -->|Publish OrderPlacedEvent| TopicOrderEvents
    TopicOrderEvents -->|Consume Event & Trừ kho| InventoryService

    InventoryService -->|Publish InventoryResponseEvent| TopicInventoryResponse
    TopicInventoryResponse -->|Consume Event & Cập nhật trạng thái| OrderService

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
            +-----+-----------+-----+       +-------+-----------+-------+
                  |           ^                     ^           |
                  |           |                     |           |
    1. Gửi event  |           | 4. Nhận phản hồi    |           | 3. Gửi phản hồi
    OrderPlaced   |           | & cập nhật order    |           | kết quả & tổng tiền
                  v           |                     |           v
            +-----------------------------------------------------------+
            |                    APACHE KAFKA BROKER                    |
            |  - Topic 'order-events' (OrderPlacedEvent)                |
            |  - Topic 'inventory-response-events' (ResponseEvent)      |
            +-----------------------------------------------------------+
                  |                                 |
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

## II. Luồng nghiệp vụ Giao tiếp qua Kafka (Cách B - Event-Driven)

1. **Khách hàng gửi yêu cầu đặt hàng:**
   - Client gửi `POST /api/orders` với body `{"productId": 1, "quantity": 2}` qua Gateway (cổng `8088`).
2. **Order Service tiếp nhận:**
   - Tạo đơn hàng trong database `order_db` với trạng thái ban đầu là `PENDING`.
   - Bắn sự kiện `OrderPlacedEvent` chứa `(orderId, productId, quantity, timestamp)` vào Kafka topic **`order-events`**.
   - Phản hồi ngay cho Client mã đơn hàng với trạng thái `PENDING` (Non-blocking, trải nghiệm mượt mà).
3. **Inventory Service xử lý sự kiện (Consumer):**
   - Lắng nghe topic **`order-events`** thông qua `InventoryOrderConsumer`.
   - Tra cứu sản phẩm trong cơ sở dữ liệu `inventory_db`:
     - **Nếu đủ hàng:** Trừ số lượng tồn kho (`quantity = quantity - orderQuantity`), tính `total_price = quantity * price`. Bắn sự kiện `InventoryResponseEvent` với status `COMPLETED` vào topic **`inventory-response-events`**.
     - **Nếu không đủ hàng hoặc sản phẩm không tồn tại:** Bắn sự kiện `InventoryResponseEvent` với status `CANCELLED` vào topic **`inventory-response-events`**.
4. **Order Service cập nhật trạng thái đơn hàng (Consumer):**
   - Lắng nghe topic **`inventory-response-events`** thông qua `OrderResponseConsumer`.
   - Cập nhật đơn hàng trong `order_db`: gán `status` (`COMPLETED` hoặc `CANCELLED`) và lưu `total_price`.

---

## III. Cấu trúc dự án

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
│   ├── build.gradle                (Spring Kafka)
│   └── src/main/
│       ├── java/com/example/inventoryservice/
│       │   ├── InventoryServiceApplication.java
│       │   ├── config/
│       │   │   ├── DataInitializer.java (Seed dữ liệu mẫu ban đầu)
│       │   │   └── KafkaConfig.java     (Kafka Producer & Consumer Configuration)
│       │   ├── consumer/
│       │   │   └── InventoryOrderConsumer.java (Lắng nghe 'order-events', trừ kho & gửi phản hồi)
│       │   ├── controller/InventoryController.java (GET /api/inventory/{productId})
│       │   ├── dto/ProductRequest.java, ProductResponse.java
│       │   ├── entity/Product.java (Bảng product thuộc inventory_db)
│       │   ├── event/OrderPlacedEvent.java, InventoryResponseEvent.java
│       │   ├── repository/ProductRepository.java
│       │   └── service/ (InventoryService, InventoryServiceImpl)
│       └── resources/application.properties (Port 8082, DB inventory_db, Kafka 9092)
│
└── order-service/                  # [Module 4] Order Service
    ├── build.gradle                (Spring Kafka)
    └── src/main/
        ├── java/com/example/orderservice/
        │   ├── OrderServiceApplication.java
        │   ├── config/KafkaConfig.java (Kafka Producer & Consumer Configuration)
        │   ├── consumer/OrderResponseConsumer.java (Lắng nghe 'inventory-response-events' & cập nhật đơn hàng)
        │   ├── controller/OrderController.java (POST /api/orders, GET /api/orders)
        │   ├── dto/OrderRequest.java, OrderResponse.java, ProductDto.java
        │   ├── entity/Order.java (Bảng orders thuộc order_db, Không dùng FK)
        │   ├── event/OrderPlacedEvent.java, InventoryResponseEvent.java
        │   ├── repository/OrderRepository.java
        │   └── service/ (OrderService, OrderServiceImpl)
        └── resources/application.properties (Port 8081, DB order_db, Kafka 9092)
```

---

## IV. Cơ sở dữ liệu (PostgreSQL)

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

## V. Hướng dẫn khởi chạy hệ thống

### 1. Yêu cầu môi trường
- Java: JDK 17 trở lên
- PostgreSQL Server đang chạy trên cổng `5432` với tài khoản `postgres` / mật khẩu `17111998`.
- Apache Kafka Broker đang chạy trên cổng `9092` (hoặc khởi chạy nhanh qua Docker bên dưới).

### 2. Chạy nhanh Kafka bằng Docker (Nếu chưa có Kafka cục bộ)
```bash
docker run -d --name kafka -p 9092:9092 apache/kafka:latest
```

### 3. Biên dịch toàn bộ dự án
```powershell
./gradlew build -x test
```

### 4. Thứ tự khởi chạy 4 dịch vụ
1. **Eureka Server:**
   ```powershell
   java -jar eureka-server/build/libs/eureka-server-0.0.1-SNAPSHOT.jar
   ```
2. **Inventory Service:**
   ```powershell
   java -jar inventory-service/build/libs/inventory-service-0.0.1-SNAPSHOT.jar
   ```
3. **Order Service:**
   ```powershell
   java -jar order-service/build/libs/order-service-0.0.1-SNAPSHOT.jar
   ```
4. **API Gateway:**
   ```powershell
   java -jar api-gateway/build/libs/api-gateway-0.0.1-SNAPSHOT.jar
   ```

---

## VI. Hướng dẫn kiểm thử các API (Qua Gateway - Port 8088)

### 1. Kiểm tra Global Filter & Lấy thông tin sản phẩm (Inventory Service)
**Request:**
```http
GET http://localhost:8088/api/inventory/1
```
**cURL:**
```bash
curl -X GET http://localhost:8088/api/inventory/1
```
**Console API Gateway in log:**
```text
[GATEWAY GLOBAL FILTER] Time: 2026-11-16 22:25:00.123 | Method: GET | URI: http://localhost:8088/api/inventory/1
```

---

### 2. Đặt hàng (POST /api/orders) - Giao tiếp bất đồng bộ qua Kafka
**Request:**
```http
POST http://localhost:8088/api/orders
Content-Type: application/json

{
  "productId": 1,
  "quantity": 2
}
```
**Kết quả phản hồi tức thì cho Client:**
```json
{
  "id": 1,
  "productId": 1,
  "quantity": 2,
  "status": "PENDING",
  "totalPrice": 0.00,
  "message": "Order placed with status PENDING. Inventory verification and stock deduction in progress via Kafka."
}
```
**Log tiến trình xử lý bất đồng bộ trong Console:**
1. **Order Service:** Bắn sự kiện `OrderPlacedEvent` vào Kafka topic `order-events`.
2. **Inventory Service:** Nhận `OrderPlacedEvent`, kiểm tra thấy đủ hàng, trừ tồn kho từ 15 xuống 13, tính `totalPrice = 3799.98`, bắn `InventoryResponseEvent` (status: `COMPLETED`).
3. **Order Service:** Nhận phản hồi, cập nhật đơn hàng #1 sang `status = "COMPLETED"`, `totalPrice = 3799.98`.

---

### 3. Tra cứu lại đơn hàng để xem kết quả sau khi Kafka xử lý xong
**Request:**
```http
GET http://localhost:8088/api/orders/1
```
**Kết quả trả về:**
```json
{
  "id": 1,
  "productId": 1,
  "quantity": 2,
  "status": "COMPLETED",
  "totalPrice": 3799.98,
  "message": "Order status: COMPLETED"
}
```

---

## VII. Task 3: Phân tích kiến trúc (Architecture Analysis)

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
- Khởi chạy thêm nhiều instance mới của `order-service` với các port khác nhau:
  ```powershell
  # Instance 1
  java -jar order-service.jar --server.port=8081
  # Instance 2
  java -jar order-service.jar --server.port=8083
  # Instance 3 (hoặc port ngẫu nhiên)
  java -jar order-service.jar --server.port=0
  ```
- Hoặc trong môi trường Kubernetes:
  ```bash
  kubectl scale deployment order-service --replicas=5
  ```
- **Tại sao Gateway không cần đổi cấu hình?**
  - Mọi instance mới khởi động đều mang cùng một `spring.application.name=order-service`.
  - Các instance này tự động đăng ký danh bạ vào Eureka Server.
  - Gateway định tuyến thông qua địa chỉ logic `uri: lb://order-service`.
  - Spring Cloud LoadBalancer tích hợp trong Gateway tự động cập nhật danh sách instance từ Eureka và phân phối request (Round Robin) đến tất cả các instance đang chạy một cách hoàn toàn trong suốt.

#### 2. Kết hợp với Kafka Broker (Xử lý hàng đợi bất đồng bộ)
- Khi có lượng request khổng lồ đổ về, Order Service chỉ việc tạo đơn hàng ở trạng thái `PENDING` và đẩy event vào Kafka topic.
- Kafka đóng vai trò như một **Buffer (vùng đệm)** chịu tải hàng chục nghìn msg/s mà không làm nghẽn thread của ứng dụng, tránh sập hệ thống (Backpressure).

#### 3. Tối ưu Connection Pool & Database
- Mở rộng số lượng connection trong HikariCP của Order Service.
- Sử dụng cơ chế Read/Write Replica cho cơ sở dữ liệu `order_db`.

---

### Câu 3: So sánh ưu/nhược điểm của phương thức giao tiếp qua OpenFeign (Đồng bộ) so với Kafka (Bất đồng bộ) trong bài toán này

| Tiêu chí | OpenFeign (Đồng bộ - Synchronous HTTP) | Apache Kafka (Bất đồng bộ - Asynchronous Event-Driven) |
| :--- | :--- | :--- |
| **Bản chất giao tiếp** | Request - Response qua HTTP/REST API. Client gửi request và **phải chờ (blocking)** kết quả trả về từ Server. | Event-Driven qua Message Broker. Producer bắn sự kiện (event) vào Topic và lập tức kết thúc lượt xử lý mà **không phải chờ**. Consumer tự pull và xử lý sau. |
| **Ưu điểm** | - **Đơn giản, trực quan, dễ debug:** Dễ viết code (`@FeignClient`), luồng xử lý tuần tự tương tự gọi hàm cục bộ.<br>- **Phản hồi tức thì cho người dùng:** Khách hàng biết ngay lập tức đơn hàng đã thành công, hết hàng hay mã lỗi chi tiết.<br>- **Kiểm tra tính nhất quán dữ liệu tại thời điểm thực:** Dữ liệu tồn kho được đọc trực tiếp từ database của Inventory Service tại thời điểm đặt hàng.<br>- **Chi phí hạ tầng thấp:** Không cần cài đặt và vận hành hệ thống message broker phức tạp. | - **Loose Coupling (Độc lập tối đa):** Order Service và Inventory Service hoàn toàn tách rời, không cần biết service kia ở đâu hay có đang online không.<br>- **Hiệu năng và thông lượng cực cao:** Không bị nghẽn I/O chờ network, Order Service có thể nhận hàng chục nghìn đơn hàng/giây và đẩy vào Kafka Topic.<br>- **Khả năng chịu lỗi vượt trội (Fault Tolerance):** Nếu Inventory Service tạm thời bị sập hoặc bảo trì, tin nhắn vẫn được lưu an toàn trên Kafka Broker. Khi service sống lại sẽ tiếp tục xử lý mà không làm mất đơn.<br>- **Dễ mở rộng tính năng mới:** Nhiều consumer khác (Notification Service gửi email, Analytics Service, Recommendation) có thể cùng subscribe topic `order-events` mà không cần sửa code Order Service. |
| **Nhược điểm** | - **Tight Coupling (Phụ thuộc sẵn sàng):** Nếu Inventory Service bị chậm hoặc chết, Order Service sẽ bị block thread, dẫn đến lỗi hàng loạt và có nguy cơ sập dây chuyền (Cascading Failure).<br>- **Thông lượng bị giới hạn:** Thời gian hoàn tất request bằng tổng thời gian của Order Service + Inventory Service + độ trễ mạng.<br>- **Khó khăn khi scale chịu tải đột biến:** Lượng request lớn dồn dập sẽ đánh sập cả 2 service cùng lúc. | - **Phức tạp về mặt kiến trúc & debug:** Khó theo dõi luồng dữ liệu (cần Distributed Tracing như Zipkin/Micrometer), khó debug lỗi giao dịch.<br>- **Tính nhất quán sau (Eventual Consistency):** Khách hàng không nhận được kết quả cuối cùng ngay lập tức, cần cơ chế thông báo trạng thái đơn hàng (WebSocket / SSE / Polling).<br>- **Phải xử lý bài toán Transaction phân tán (Saga Pattern):** Nếu trừ kho thất bại, phải phát event bù trừ (Compensating Transaction) để hoàn lại tiền hoặc hủy đơn.<br>- **Chi phí tài nguyên & vận hành:** Cần cài đặt, cấu hình cluster ZooKeeper/KRaft, phân vùng (partitions), quản lý offset, xử lý tin nhắn trùng (Idempotency). |
| **Áp dụng phù hợp cho bài toán** | Phù hợp với hệ thống quy mô vừa và nhỏ, luồng nghiệp vụ đơn giản cần trả về kết quả thành công/thất bại ngay lập tức cho client. | Phù hợp với các sàn thương mại điện tử lớn (Shopee, Tiki, Amazon), các đợt Flash Sale lưu lượng cực lớn, hệ thống có nhiều service vệ tinh (thanh toán, kho, vận chuyển, email). |
