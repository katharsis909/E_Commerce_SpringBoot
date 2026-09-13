(() => {
  const input = document.querySelector('#product-search');
  const list = document.querySelector('#suggestion-list');
  const status = document.querySelector('#search-status');
  const delayMs = 250;
  let timer;
  let version = 0;
  let activeRequest;

  function clearSuggestions(message = '') {
    list.replaceChildren();
    input.setAttribute('aria-expanded', 'false');
    status.textContent = message;
  }

  function showSuggestions(suggestions) {
    list.replaceChildren(...suggestions.map((product) => {
      const item = document.createElement('li');
      item.className = 'suggestion';
      item.role = 'option';
      item.textContent = product.name;
      item.addEventListener('mousedown', (event) => {
        event.preventDefault();
        input.value = product.name;
        clearSuggestions();
        window.location.assign(`/view/product/${encodeURIComponent(product.name)}`);
      });
      return item;
    }));
    input.setAttribute('aria-expanded', String(suggestions.length > 0));
    status.textContent = suggestions.length ? `${suggestions.length} suggestions` : 'No matching products';
  }

  input.addEventListener('input', () => {
    const query = input.value.trim();
    const requestVersion = ++version;
    clearTimeout(timer);
    activeRequest?.abort();

    if (!query) {
      clearSuggestions();
      return;
    }

    status.textContent = 'Waiting…';
    timer = window.setTimeout(async () => {
      const controller = new AbortController();
      activeRequest = controller;
      status.textContent = 'Searching…';

      try {
        const response = await fetch(`/search/autocomplete/${encodeURIComponent(query)}`, {
          signal: controller.signal,
          headers: { 'X-Search-Version': String(requestVersion) }
        });

        // Abort is best-effort. The version check is the final authority on
        // whether an out-of-order response is allowed to change the dropdown.
        if (requestVersion !== version) return;
        if (response.status === 204) return clearSuggestions('No matching products');
        if (!response.ok) throw new Error(`Search failed (${response.status})`);
        showSuggestions(await response.json());
      } catch (error) {
        if (error.name !== 'AbortError' && requestVersion === version) {
          clearSuggestions('Suggestions are temporarily unavailable');
        }
      }
    }, delayMs);
  });

  input.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') clearSuggestions();
  });
})();
