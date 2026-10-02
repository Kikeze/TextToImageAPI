'use strict';

const form = document.getElementById('image-form');
const textInput = document.getElementById('text');
const fontInput = document.getElementById('font');
const useColor = document.getElementById('use-color');
const colorInput = document.getElementById('color');
const submitButton = document.getElementById('submit');
const statusMessage = document.getElementById('status');
const errorMessage = document.getElementById('error');
const result = document.getElementById('result');
const image = document.getElementById('image');

useColor.addEventListener('change', () => {
  colorInput.disabled = !useColor.checked;
});

function describeServerError(response, body, rawBody) {
  const lines = [`Error HTTP ${response.status}${response.statusText ? `: ${response.statusText}` : ''}`];
  if (body && typeof body === 'object') {
    if (body.title) lines.push(body.title);
    if (body.detail) lines.push(body.detail);
    if (Array.isArray(body.errors)) {
      for (const error of body.errors) {
        lines.push(`${error.field || 'Solicitud'}: ${error.message || 'Valor inválido'}`);
      }
    }
    if (!body.title && !body.detail && !body.errors) lines.push(JSON.stringify(body, null, 2));
  } else {
    lines.push(rawBody || 'El servidor no devolvió detalles del error.');
  }
  return lines.join('\n');
}

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (submitButton.disabled) return;

  errorMessage.hidden = true;
  errorMessage.textContent = '';
  result.hidden = true;
  image.removeAttribute('src');
  statusMessage.textContent = 'Generando imagen…';
  submitButton.disabled = true;

  const request = { text: textInput.value };
  // Omit optional fields so the API applies its random defaults.
  if (fontInput.value !== '') request.font = fontInput.value;
  if (useColor.checked) request.color = colorInput.value;

  try {
    const response = await fetch('api/v1/text-images', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request)
    });
    const rawBody = await response.text();
    let body;
    try {
      body = JSON.parse(rawBody);
    } catch {
      body = null;
    }

    if (!response.ok) throw new Error(describeServerError(response, body, rawBody));
    if (!body || body.mimeType !== 'image/png' || typeof body.imageBase64 !== 'string') {
      throw new Error('El servidor devolvió una respuesta sin una imagen PNG válida.');
    }

    image.src = `data:image/png;base64,${body.imageBase64}`;
    document.getElementById('result-font').textContent = body.font;
    document.getElementById('result-color').textContent = body.color;
    document.getElementById('result-width').textContent = `${body.width} px`;
    document.getElementById('result-height').textContent = `${body.height} px`;
    result.hidden = false;
    statusMessage.textContent = 'Imagen generada.';
  } catch (error) {
    statusMessage.textContent = '';
    errorMessage.textContent = error instanceof TypeError
      ? 'No se pudo conectar con la API. Comprueba que la aplicación sigue ejecutándose e inténtalo de nuevo.'
      : error.message;
    errorMessage.hidden = false;
  } finally {
    submitButton.disabled = false;
  }
});

image.addEventListener('error', () => {
  result.hidden = true;
  statusMessage.textContent = '';
  errorMessage.textContent = 'No se pudo mostrar la imagen PNG devuelta por el servidor.';
  errorMessage.hidden = false;
});
