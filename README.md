# TextToImageAPI

API REST local para Windows que dibuja texto literal en una imagen PNG y devuelve sus bytes codificados en Base64 dentro de JSON.

## Requisitos y ejecución

- JDK 21, con `JAVA_HOME` apuntando al JDK y `java` disponible en `PATH`.
- Fuentes instaladas en Windows y accesibles desde Java.
- Acceso a Internet durante la primera compilación para descargar Maven y dependencias.

Desde PowerShell, en la raíz del repositorio:

```powershell
.\mvnw.cmd clean verify
java -jar .\target\text-to-image-api-0.0.1-SNAPSHOT.jar
```

También puedes ejecutar `.\mvnw.cmd spring-boot:run`. Maven Wrapper fija Maven 3.9.16; el proyecto usa Spring Boot 4.1.1 y Java 21.

La aplicación escucha en `http://127.0.0.1:8080`, sin autenticación. Se vincula únicamente a la interfaz local. Para detenerla, pulsa Ctrl+C.

## Interfaz de prueba manual

Con la aplicación ejecutándose, abre **http://127.0.0.1:8080/** en el navegador.
La página permite introducir texto, elegir opcionalmente una fuente y activar un selector de color.
Al enviar la solicitud muestra el PNG y la fuente, el color, el ancho y el alto devueltos por la API.
También presenta los errores del servidor y los mensajes de validación de cada campo.
Deja la fuente vacía y la opción de color desmarcada para usar los valores aleatorios.
Si cambias el puerto de la aplicación, utiliza ese puerto en la URL.

## Endpoint

`POST /api/v1/text-images`, con `Content-Type: application/json`.

```json
{
  "text": "Hola, mundo",
  "font": "Arial",
  "color": "#2457C5"
}
```

`text` es obligatorio y no puede estar vacío ni contener solamente espacios. `font` y `color` son opcionales: ausentes o `null` activan sus valores aleatorios; las cadenas vacías son inválidas. La fuente debe ser una familia disponible en el servidor, sin distinguir mayúsculas y minúsculas. No se reciben rutas ni archivos de fuentes. `color` acepta exclusivamente `#RRGGBB`; se devuelve normalizado a mayúsculas.

Ejemplo de respuesta HTTP 200 (Base64 y dimensiones ilustrativos):

```json
{
  "imageBase64": "<bytes PNG en Base64>",
  "mimeType": "image/png",
  "font": "Arial",
  "color": "#2457C5",
  "width": 240,
  "height": 80
}
```

Base64 es puro, sin prefijo `data:`. La fuente y el color de respuesta son los efectivamente utilizados.

Solicitud mínima y guardado del PNG desde PowerShell:

```powershell
$body = @{ text = 'Hola, mundo' } | ConvertTo-Json
$result = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/v1/text-images' `
    -Method Post -ContentType 'application/json; charset=utf-8' `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
[System.IO.File]::WriteAllBytes(
    (Join-Path $PWD 'imagen.png'),
    [System.Convert]::FromBase64String($result.imageBase64)
)
```

## Reglas de renderizado

- Texto literal, estilo normal de 32 px, fondo blanco opaco y márgenes de 20 px.
- Dimensiones automáticas; ancho máximo total de 1.200 px, incluidos los márgenes.
- Saltos de línea preservados, incluidas líneas vacías y el salto final. CRLF y CR se normalizan a LF; cada tabulador se convierte en cuatro espacios.
- Ajuste de líneas al ancho máximo, también para palabras sin espacios. Se miden tanto los avances como los límites visuales para conservar márgenes y evitar recortes.
- Máximo de 2.000 puntos de código Unicode, contados antes de normalizar el texto, y 4.000.000 de píxeles. Las dimensiones se validan antes de crear el bitmap; no se trunca el texto.
- Fuente aleatoria entre las familias detectadas al arrancar que pueden representar todos los caracteres. Se selecciona uniformemente entre esas familias, y puede repetirse entre solicitudes.
- Si la fuente solicitada no existe o no representa el texto, o si ninguna fuente aleatoria lo representa, se devuelve HTTP 400. No se combinan fuentes. Emojis y alfabetos adicionales dependen del soporte real de las fuentes y de Java 2D; no se garantiza emoji a color.
- Color aleatorio RGB con una relación de contraste de al menos 4,5 respecto al fondo. Un color explícito se respeta, incluso si tiene poco contraste.
- Se rechazan controles distintos de tabulador y saltos de línea y secuencias Unicode con sustitutos sin pareja.

El catálogo incluye las familias que expone Java, incluidas sus fuentes lógicas. Reinicia el servidor tras instalar fuentes. El JDK debe incluir `java.desktop`; el proceso se ejecuta en modo headless y no abre ventanas. El contenido de los PNG puede variar entre equipos debido a sus fuentes y al renderizador.

## Configuración

Los valores están en `src/main/resources/application.yml` y admiten las sobreescrituras habituales de Spring Boot:

| Propiedad | Valor predeterminado |
| --- | --- |
| `server.address` | `127.0.0.1` |
| `server.port` | `8080` |
| `rendering.font-size` | `32` |
| `rendering.margin` | `20` |
| `rendering.max-width` | `1200` |
| `rendering.max-code-points` | `2000` |
| `rendering.max-pixels` | `4000000` |
| `rendering.background` | `#FFFFFF` |

Ejemplo: `java -jar .\target\text-to-image-api-0.0.1-SNAPSHOT.jar --server.port=8081`.

## Errores

Los errores usan `application/problem+json` con `status`, `title`, `detail` e `instance`; las validaciones del DTO incluyen `errors` con campo y mensaje.

- **400:** texto inválido, límite excedido, color inválido, fuente inexistente o caracteres sin soporte; también JSON mal formado.
- **405:** método HTTP no permitido.
- **415:** formato de entrada distinto de JSON.
- **500:** fallo interno de generación; se registra en el servidor y se devuelve un mensaje genérico sin traza interna.

## Arquitectura y pruebas

`api` contiene controlador, DTOs y tratamiento de errores; `service` coordina la conversión y contiene catálogo de fuentes, resolución de colores y renderizador; `config` contiene las propiedades validadas al arrancar.

La conversión es síncrona, en memoria y sin persistencia. Cada solicitud utiliza su propio bitmap y contexto gráfico, que se libera al terminar. El catálogo de familias es inmutable.

`RenderingTest` verifica PNG, fondo, presencia de texto, márgenes, saltos de línea, palabras largas, límites de píxeles, fuentes, contraste, Unicode y solicitudes concurrentes. `TextImageApiTest` levanta el servidor en un puerto aleatorio y prueba por HTTP el contrato JSON, campos opcionales, errores, límites y decodificación de PNG. No exige imágenes idénticas entre sistemas ni que dos selecciones aleatorias sean distintas.

```powershell
.\mvnw.cmd clean verify
```

El JAR ejecutable queda en `target/`; los informes de pruebas, en `target/surefire-reports/`.
