# NullPointer POS · Wendy’s Food

**Equipo de Desarrollo (Grupo 1):**
- **Randy Huamán** (`feature/comandas-huaman`): Arquitectura KMP, UI Shell, Módulo de Salón y Comandas, Cliente HTTP Ktor.
- **Emanuel Janampa** (`feature/cocina-janampa`): Monitor KDS Cocina, WebSockets STOMP en tiempo real, Notificación deslizable animada, Control de Platos Agotados.
- **Daniel Mamani** (`feature/caja-mamani`): Módulo de Caja y Facturación, Cobro Efectivo/Yape/Plin/Tarjeta, Simulador POS Propio (Luhn), Emisión Boleta/Factura SUNAT Beta con Código QR y Ticket Térmico.

Aplicación multiplataforma en Kotlin Multiplatform (Android e iOS) con Jetpack / Compose Multiplatform, arquitectura Clean + MVVM, sincronización offline-first y soporte integral para los microservicios backend Spring Boot 3 de Wendy's Food.

## Módulos y Funcionalidades Principales
1. **Acceso y Seguridad**: Autenticación JWT con roles (Mozo, Cocinero, Cajero, Administrador, Almacenero).
2. **Salón y Comandas (Mozo)**: Distribución de mesas por zonas, toma rápida de pedidos, personalización de platos con notas/observaciones, borradores locales y traslados.
3. **Monitor KDS Cocina (Cocinero)**: Recepción de comandas en tiempo real vía WebSocket STOMP (`/topic/cocina`), **banner deslizable animado con aviso sonoro al recibir nuevos pedidos**, temporizador de espera con alertas de color y marcado de platos listos por estación.
4. **Caja y Facturación (Cajero)**:
   - Apertura y cierre de turno con arqueo de efectivo y cálculo de descuadre.
   - Cobro multicanal: Efectivo con cálculo de vuelto, Yape / Plin con código de operación, **Tarjeta mediante Simulador POS propio con validación Luhn**, y Pagos Mixtos.
   - **Emisión de Comprobantes Electrónicos con SUNAT Beta**: Emisión automática de Boletas y Facturas, validación de clientes en SUNAT (DNI/RUC), generación de código Hash y Código QR estándar para tiquetera térmica (80mm) y WhatsApp.
5. **Almacén e Inventario (Almacenero)**: Control de insumos, alertas de stock mínimo, registro de mermas y recetas.
6. **Reportes y Auditoría (Administrador)**: Ventas por hora, platos más pedidos y trazabilidad de pedidos.

## Estructura de Ramas Git
- `main`: Rama de producción estable. Contiene la versión final etiquetada con `v1.0-final`.
- `develop`: Rama integradora donde se fusionan los avances revisados de cada miembro.
- `feature/comandas-huaman`: Trabajo de salón, login, comandas y arquitectura base.
- `feature/cocina-janampa`: Trabajo de monitor KDS, websockets, notificación deslizable e inventario.
- `feature/caja-mamani`: Trabajo de caja, simulador de tarjeta y facturación electrónica SUNAT Beta.

No contiene backend, APK ni IPA; es el proyecto fuente. No requiere Docker para editar o compilar la aplicación. Para operar necesita que los servicios del backend estén en ejecución, mediante el método que utilices para ese proyecto.

## Abrir en Android Studio

1. Extraer el ZIP en una carpeta local y abrir la raíz `NullPointerPOS`.
2. Utilizar JDK 21 (el JBR de Android Studio sirve), Android SDK 36 y aceptar la sincronización de Gradle. Las versiones base del catálogo y el wrapper proceden del proyecto Pharma entregado; el plugin de serialización se alinea con Kotlin.
3. Seleccionar `androidApp` y ejecutar sobre Android 7/API 24 o superior.
4. En Windows también puedes usar `gradlew.bat :androidApp:assembleDebug`. El APK se genera en `androidApp/build/outputs/apk/debug/`.

La primera sincronización necesita internet para descargar Gradle y las dependencias. No se incluye `local.properties`: Android Studio configura la ruta de SDK de tu computadora.

## Conectar con tu backend

En el acceso, abrir **Conexión al restaurante**, introducir la URL base del gateway y pulsar **Guardar conexión**. No agregar `/api/v1`: la aplicación lo agrega a cada ruta.

| Dispositivo | URL de ejemplo |
|---|---|
| Emulador Android con backend en Windows | `http://10.0.2.2:8080` (valor inicial Android) |
| Teléfono Android o iPhone físico | `http://192.168.1.10:8080` (reemplazar por la IP real del servidor) |
| Simulador iOS con backend en el mismo Mac | `http://localhost:8080` (valor inicial iOS) |

El dispositivo y el servidor deben compartir una red accesible. El gateway debe escuchar en una interfaz accesible, permitir el puerto 8080 en el firewall y tener sus servicios registrados. En un teléfono, `localhost` es el propio teléfono.

Las peticiones de negocio usan exclusivamente el gateway. El cliente no se conecta directamente a PostgreSQL, RabbitMQ ni a los puertos internos.

Las cuentas iniciales del backend de referencia son `ADM001`, `MOZ001`, `MOZ002`, `COC001`, `CAJ001`, con PIN `1234`, si se conservaron los datos iniciales. Los roles cajero y almacenero pueden acceder a su cuenta, pero no tienen módulos operativos en esta entrega.

## iPhone

Se necesita un **Mac con Xcode compatible con el proyecto (formato Xcode 16)** y JDK 21 para compilar. Los targets compartidos son `iosArm64` y `iosSimulatorArm64` (simulador Apple Silicon).

1. Abrir `iosApp/iosApp.xcodeproj`.
2. Seleccionar el target `iosApp`, configurar tu equipo de firma en Signing & Capabilities o `TEAM_ID` en `iosApp/Configuration/Config.xcconfig`, y elegir el dispositivo.
3. Dar permiso de ejecución al wrapper si es necesario: `chmod +x gradlew` desde la raíz.
4. Xcode ejecuta `:shared:embedAndSignAppleFrameworkForXcode`; el framework se llama `Shared`.
5. Aceptar el permiso de red local e introducir la IP del servidor cuando el backend esté en Windows u otra computadora.

El despliegue iOS heredado es 18.2. No se incluye un certificado ni un IPA firmado.

## Uso

- **Mozo:** acceder → Mesas → elegir mesa libre → Menú → agregar platos → Resumen → cantidades/notas → guardar o enviar. Guardar crea un borrador en el servidor y ocupa la mesa, tal como establece su trigger.
- La comanda local se conserva por usuario, servidor y mesa. Puedes abandonar la pantalla y volver. Cerrar sesión no elimina tus borradores.
- Tras guardar se muestra el ID del pedido. Conviene conservarlo: el backend no tiene consulta de pedido activo por mesa. La aplicación conserva los ID conocidos en este dispositivo y permite recuperarlos manualmente.
- **Mesa ocupada sin referencia local:** introducir el ID del pedido. El servidor valida la propiedad y el rol. No se crea otro pedido sobre esa mesa.
- **Traslado:** abrir el resumen de una comanda guardada, elegir una mesa libre y confirmar. El backend verifica nuevamente la disponibilidad. Los cambios locales no guardados se mantienen; pulsa Guardar para persistirlos.
- **Cocinero:** consulta inicial REST y suscripción STOMP. Hay un respaldo REST cada 15 s. Amarillo desde 10 min y rojo desde 15 min; los temporizadores dependen de la hora del dispositivo y la fecha de envío del servidor.
- **Administrador:** acceso a salón, cocina y usuarios; puede crear, activar o desactivar cuentas conforme a las validaciones del backend. No puede desactivar su propia cuenta desde la interfaz.

## Red y consistencia

El PIN y el token no se guardan en disco. Se solicita acceso al abrir de nuevo el proceso; la sesión vive en memoria. Los borradores con sus notas e ID quedan en almacenamiento privado local y se separan por servidor y trabajador. No se almacenan datos de pago.

Si falla la creación y se desconoce si el servidor la confirmó, la app bloquea otro POST para impedir duplicados. Debes verificar el pedido en el backend (por ejemplo con Postman o la BD) y recuperarlo por ID. El servidor no tiene claves de idempotencia ni consulta por mesa; no existe recuperación automática segura en ese caso.

Si falla el envío después de guardar, reintentar primero consulta el pedido por ID: si ya salió de BORRADOR no vuelve a enviarlo. La confirmación significa que el backend registró el envío y su evento; no prueba que el cocinero lo haya visto. RabbitMQ y el outbox siguen siendo responsabilidad del servidor.

La nota general solo se puede definir antes de crear el pedido porque el endpoint de edición acepta únicamente detalles. Las observaciones por plato admiten hasta 500 caracteres y la nota general hasta 1000, conforme a los DTO reales.

La configuración HTTP local está habilitada en ambas plataformas para este backend. Para usarlo fuera de la red local, configurar HTTPS y revisar la configuración de transporte antes de distribuirlo.

## Documentación

- `docs/CONTRATO_API.md`: rutas y payloads exactos.
- `docs/ALCANCE_Y_TRAZABILIDAD.md`: correspondencia con Excel y limitaciones.
- `docs/VERIFICACION.md`: comprobaciones realizadas y límites de validación.

No se han cambiado los archivos originales del backend ni del Excel.
 
  
  
  
 