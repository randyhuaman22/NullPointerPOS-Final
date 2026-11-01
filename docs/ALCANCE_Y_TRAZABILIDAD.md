# Alcance y Trazabilidad Full-Stack (Sprints 1, 2 y 3)

**Proyecto:** NullPointer POS – Sistema de Gestión de Pedidos, Cocina y Caja  
**Restaurante:** Pollería & Parrillas "Wendy's Food"  
**Autor:** Randy Huamán (Célula G5 - UPeU)  
**Integrantes:** Randy Huamán, Emanuel Janampa, Daniel Mamani  
**Arquitectura:** Kotlin Multiplatform (KMP), Compose Multiplatform, Clean Architecture (Domain, Data, Presentation, Theme), Spring Cloud Gateway (:8080), PostgreSQL, RabbitMQ, WebSocket/STOMP.

---

## Matriz de Cobertura y Trazabilidad por Historias de Usuario

| HU | Sprint | Módulo | Implementación Full-Stack en esta Entrega |
|---|---|---|---|
| **HU-01** | 1 | Autenticación | Login táctil con código de colaborador y teclado numérico de PIN de 4 dígitos. Diálogo para configurar URL del Gateway (`http://HOST:8080`). JWT Bearer en memoria (`SessionContext`). |
| **HU-02** | 1 | Salón | Cuadrícula dinámica de mesas con tarjetas redondeadas (18.dp), estado semántico (Verde Esmeralda = Libre, Rojo Coral = Ocupada), capacidad en comensales, área y selector por pestañas. |
| **HU-03** | 1 | Menú | Catálogo de platos agrupados por categorías gastronómicas (Pollo a la Brasa, Parrillas, Bebidas, Guarniciones, Postres) con buscador reactivo y avatar alfabético. |
| **HU-04** | 1 | Comanda | Observaciones por plato de hasta 300 caracteres con chips de sugerencia rápida ("Sin mayonesa", "Papas bien crocantes", "Ají extra", "Pechuga"). |
| **HU-05** | 1 | Comanda | Stepper táctil de cantidades (+ / -), botón de eliminación con diálogo de confirmación, cálculo automático de subtotal, IGV (18%) y total. |
| **HU-06** | 1 | Salón / Mesas | Traslado de comanda a mesa libre con motivo opcional mediante `POST /api/v1/pedidos/traslado`. |
| **HU-07** | 1 | Comanda | Guardado local en borrador, confirmación modal y envío a cocina vía `POST /api/v1/pedidos`. Manejo de incertidumbre y recuperación por ID. |
| **HU-26** | 1 | Usuarios | Administración de colaboradores (Mozos, Cajeros, Cocineros, Almaceneros, Admins), creación con validación de PIN y activación/desactivación. |
| **HU-08** | 2 | Cocina (KDS) | Tablero KDS en tiempo real con WebSocket/STOMP (`/ws/cocina`, tópico `/topic/cocina`), orden cronológico de comandas entrantes, detalle de platos y observaciones. |
| **HU-09** | 2 | Cocina (KDS) | Cronómetro de espera en vivo con semáforo gastronómico: Normal (<10 min, Verde), Preventiva (10-15 min, Ámbar), Crítica (>=15 min, Rojo Coral pulsante). |
| **HU-10** | 2 | Cocina (KDS) | Botón táctil individual por plato para marcar "Listo ✓" consumiendo `PATCH /api/v1/pedidos/detalle/{id}` con `{ "estado": "LISTO" }`. |
| **HU-11** | 2 | Cocina (KDS) | Filtro de comandas por estado ("Todos", "Enviados", "En preparación", "Listos") y búsqueda por texto. |
| **HU-12** | 2 | Cocina (KDS) | Modal de gestión de platos agotados consumiendo `PATCH /api/v1/menu/disponibilidad` para bloquear o reactivar platos al instante en salón. |
| **HU-13** | 2 | Caja | Apertura de turno de caja registrando fondo inicial en efectivo mediante `POST /api/v1/caja/apertura`. Control de turno activo. |
| **HU-14** | 2 | Caja | Cobro en efectivo con presets de billetes (`Exacto`, `S/ 50`, `S/ 100`, `S/ 200`), digitación de monto recibido y cálculo automático y destacado de vuelto. |
| **HU-15** | 2 | Caja | Cobro digital (Yape / Plin) con código QR simulado de Wendy's Food y registro de número de operación (6-8 dígitos). |
| **HU-16** | 2 | Caja | Cobro mixto dividiendo el importe entre efectivo y digital, y cierre de turno con arqueo de caja físico ingresando diferencias y observaciones. |
| **HU-17** | 3 | Descuentos | Aplicación de descuentos porcentuales (5%, 10%, 15%, Cortesía 100%) y motivo mediante `PATCH /api/v1/pedidos/{idPedido}/descuento`. |
| **HU-18** | 3 | Inventario | Catálogo de insumos (pollo, papas, carbón, aceite, gaseosas) con unidad de medida, stock actual, stock mínimo y costo unitario. |
| **HU-19** | 3 | Inventario | Barra visual de salud de stock y alertas automáticas de materias primas en nivel crítico o por debajo del stock mínimo. |
| **HU-20** | 3 | Inventario | Registro de compras de insumos para ingreso al almacén y registro de mermas/desperdicios de cocina. |
| **HU-21** | 3 | Inventario | Mapeo de recetas técnicas por plato vinculando ingredientes e insumos descontables. |
| **HU-22** | 3 | Reportes | Reporte gráfico y analítico de ventas por hora para identificar horas punta de atención. |
| **HU-23** | 3 | Reportes | Ranking de platos más populares y vendidos con barras proporcionales y recaudación total. |
| **HU-24** | 3 | Reportes | Exportación de reportes de venta a hoja de cálculo Excel (`.xlsx`). |
| **HU-25** | 3 | Auditoría | Registro inmutable de auditoría para tracking de anulaciones, traslados y descuentos aplicados. |

---

## Sistema de Diseño "Bistro & Gourmet Contemporáneo" (Google Stitch)

La interfaz gráfica Compose Multiplatform fue rediseñada por completo basándose en las especificaciones del prototipo Google Stitch:
- **Borgoña Bistro (`#82171E`):** Color de identidad de marca, barra de acciones primarias y botones destacados.
- **Ámbar Dorado (`#F59E0B` / `#D97706`):** Color de acento cálido, chips de sugerencia, notas y alertas preventivas.
- **Fondo Crema Marfil (`#FAF8F5`):** Superficie cálida que reduce la fatiga visual en turnos prolongados de trabajo.
- **Superficie de Tarjetas (`#FFFFFF` con borde `#E7E1D8`):** Tarjetas con elevación suave y bordes redondeados (14.dp a 18.dp).
- **Semántica Operativa:** Verde Esmeralda (`#10B981`) para Libre/Listo/Completado; Rojo Coral (`#EF4444`) para Ocupado/Crítico.
