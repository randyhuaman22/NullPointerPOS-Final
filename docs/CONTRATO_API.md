# Contrato de API y Microservicios (Spring Cloud Gateway :8080)

Todas las peticiones desde la aplicación Kotlin Multiplatform se dirigen al API Gateway en:
`http://HOST:8080/api/v1/`

Autenticación: Cabecera HTTP `Authorization: Bearer <token_jwt>` (excepto en login).

---

## 1. Autenticación y Seguridad
- `POST /api/v1/autenticacion/iniciar-sesion`: `{ "codigoTrabajador": String, "clave": String }` -> `LoginResponse(token, usuario)`
- `GET /api/v1/usuarios`: Lista de colaboradores y roles (`ADMINISTRADOR`, `MOZO`, `CAJERO`, `COCINERO`, `ALMACENERO`).
- `POST /api/v1/usuarios`: Creación de colaborador.
- `PATCH /api/v1/usuarios/{id}/activo`: `{ "activo": Boolean }`

## 2. Salón, Mesas y Menú
- `GET /api/v1/mesas`: Lista de mesas activas con código, área, capacidad y estado (`LIBRE`, `OCUPADA`).
- `GET /api/v1/menu/categorias`: Lista de categorías gastronómicas ordenadas.
- `GET /api/v1/menu/productos`: Catálogo de platos y bebidas con disponibilidad.
- `PATCH /api/v1/menu/disponibilidad`: `{ "idProducto": Long, "disponible": Boolean }` (HU-12).

## 3. Comandas y Pedidos
- `POST /api/v1/pedidos`: `{ "idMesa": Long, "observaciones": String, "detalles": [...] }` (HU-07).
- `GET /api/v1/pedidos/{id}`: Consulta de pedido y estado.
- `PUT /api/v1/pedidos/{id}`: Modificación de líneas en borrador.
- `POST /api/v1/pedidos/traslado`: `{ "idPedido": Long, "idMesaDestino": Long, "motivo": String }` (HU-06).
- `PATCH /api/v1/pedidos/{id}/descuento`: `{ "porcentaje": Double, "motivo": String }` (HU-17).
- `POST /api/v1/pedidos/{id}/anular`: `{ "motivo": String }`

## 4. KDS Cocina (WebSocket & REST)
- `WebSocket STOMP`: `ws://HOST:8080/ws/cocina`, suscripción a `/topic/cocina`.
- `GET /api/v1/cocina/pedidos`: Lista de pedidos activos en cocina ordenados cronológicamente.
- `PATCH /api/v1/pedidos/detalle/{id}`: `{ "estado": "LISTO" }` (HU-10).

## 5. Caja, Cobros y Facturación SUNAT
- `POST /api/v1/caja/apertura`: `{ "montoInicial": Double }` -> `TurnoCaja`
- `GET /api/v1/caja/turno-abierto`: Consulta de turno activo del cajero.
- `POST /api/v1/caja/cobrar`: `{ "idPedido": Long, "idTurno": Long, "montoRecibido": Double, "importeTotal": Double, "idempotencyKey": String }`
- `POST /api/v1/caja/cobrar-digital`: `{ "idPedido": Long, "idTurno": Long, "metodoPago": String, "importeTotal": Double, "numeroOperacion": String, "ultimosCuatroDigitos": String, "idempotencyKey": String }`
- `POST /api/v1/caja/cobrar-mixto`: Cobro combinado efectivo + digital.
- `POST /api/v1/caja/cierre`: `{ "idTurno": Long, "totalEfectivoReal": Double, "motivoDescuadre": String }`
- `GET /api/v1/clientes/{tipoDocumento}/{numeroDocumento}`: Búsqueda SUNAT (DNI/RUC) para Boletas y Facturas.
- `POST /api/v1/tickets/whatsapp`: `{ "idPedido": Long, "telefono": String, "idTurno": Long }`

## 6. Inventario y Almacén
- `GET /api/v1/inventario/insumos`: Lista de insumos y materias primas.
- `POST /api/v1/inventario/insumos`: Alta de nuevo insumo.
- `GET /api/v1/inventario/alertas`: Lista de insumos por debajo del stock mínimo.
- `POST /api/v1/inventario/compras`: Registro de compra y entrada a almacén.
- `POST /api/v1/inventario/mermas`: Registro de merma o pérdida.
- `POST /api/v1/inventario/recetas`: Asignación de ingredientes por plato.

## 7. Analítica y Reportes
- `GET /api/v1/reportes/ventas-por-hora`: Total recaudado y pedidos por intervalo horario.
- `GET /api/v1/reportes/platos-populares`: Ranking de platos más solicitados.
- `GET /api/v1/auditoria/pedidos`: Trazabilidad inmutable de descuentos, anulaciones y cambios.
