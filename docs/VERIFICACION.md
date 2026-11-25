# Verificación de la entrega

## Ajuste de interfaz del 25 de noviembre de 2026

Se recuperaron las pantallas, el menú lateral, los botones y la distribución de la app original. Se añadieron transiciones suaves a filtros y mesas, ligeros ajustes de bordes y contraste de las barras del sistema.

Se conservan las correcciones de API, la distinción entre Yape y Plin, los importes negativos, las claves de idempotencia de cobro y la recuperación de comprobantes pendientes. El estado de emisión se muestra según la respuesta del backend.

Las comprobaciones de Android se ejecutan sobre esta versión. La compilación iOS requiere macOS y Xcode. El registro que sigue corresponde a la entrega inicial.

---

Autor: Randy Huaman.

## Comprobado

- Configuración del proyecto con `gradlew help`: **BUILD SUCCESSFUL**.
- Compilación del código compartido con `gradlew :shared:compileCommonMainKotlinMetadata`: **BUILD SUCCESSFUL**. Incluye la UI Compose, ViewModel, casos de uso, contratos, API y repositorios compartidos.
- Contraste estático de **14 métodos REST** con los controladores Java originales, incluyendo rutas dinámicas, activación/desactivación y contenedor de respuesta del envío.
- Campos de los DTO de entrada contrastados con las solicitudes del backend.
- Manifiesto Android, vector del icono, plist iOS y JSON de recursos: sintaxis válida.
- Proyecto iOS actualizado: sin llamada heredada a `KoinIosKt`, framework `Shared` y entrada `MainViewControllerKt` del proyecto nuevo.
- Autoría propia: Randy Huaman. Wrapper y dependencias conservan sus avisos originales.
- Archivo ZIP verificado con lectura integral y control de CRC; sin cachés de Gradle, resultados de compilación, configuración local de SDK ni datos del usuario.

## No ejecutado

- No se utilizó Docker ni se arrancaron servicios del backend.
- No se hicieron peticiones contra el servidor real, pruebas de concurrencia, rendimiento ni mediciones de los objetivos de 1–2 segundos del Excel.
- No se compiló un APK completo ni se ejecutó sobre un dispositivo Android. La compilación de metadatos compartidos no valida el empaquetado Android ni reemplaza una prueba visual en teléfono.
- No se compiló iOS ni se firmó un IPA: este entorno es Windows, y esas operaciones requieren macOS/Xcode.
- Se incluyen tres pruebas unitarias pequeñas de importes y contratos HTTP en `shared/src/commonTest`, preparadas para ejecutarse con el entorno de la plataforma configurado; no se declara su ejecución en esta entrega.

## Comprobación manual recomendada al levantar el backend

1. Configurar la URL del gateway desde la pantalla de acceso.
2. Ingresar como mozo, elegir mesa libre, agregar un plato con nota, guardar y conservar el ID.
3. Enviar y abrir cocina con una cuenta de cocinero en otro dispositivo para verificar recepción.
4. Ingresar como administrador para verificar creación y cambio de estado de una cuenta de prueba.

Los límites funcionales y las diferencias con el Excel están detallados en `ALCANCE_Y_TRAZABILIDAD.md`. Esta entrega implementa las operaciones existentes, sin afirmar que el backend suministrado ya cubra todo el Sprint 2.
