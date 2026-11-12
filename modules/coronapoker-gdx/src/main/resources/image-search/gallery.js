(() => {
  'use strict';

  const english = new URLSearchParams(location.search).get('lang') === 'en';
  const copy = english ? {
    local: 'Favorites', finder: 'Search', clear: 'Clear', cancel: 'Cancel',
    receive: on => 'Received: ' + (on ? 'Yes' : 'No'),
    question: 'Clear all favorites?',
    purgeCache: 'Clear image cache',
    purgeQuestion: 'Clear cached images from chat and Search? Favorites are kept.',
    confirmPurge: size => 'Yes, clear the cache (' + size + ')',
    cacheSizing: 'Calculating size…', cacheSizeFailed: 'Could not read the image cache size',
    cacheEmpty: 'There are no cached images to clear',
    cachePurged: size => 'Cache cleared (' + size + ' freed)', cacheFailed: 'Could not clear the image cache',
    previous: 'Previous', next: 'Next', page: (n, total) => 'Page ' + n + ' / ' + total,
    searchPage: n => 'Page ' + n, loadingMore: 'Looking for more images…',
    moreError: 'Could not load more images. Try again.',
    localEmpty: 'No favorites yet', localHelp: 'Find images or GIFs to add to your favorites.',
    sendImage: 'Send image to chat', sendFailed: 'Could not send', unavailable: 'Unavailable',
    removeImage: 'Remove from favorites', removeFailed: 'Could not remove', clearSearch: 'Clear search field',
    history: 'Saved searches', removeSearch: term => 'Delete search: ' + term,
    search: 'Search', query: 'Search for images or GIFs',
    instructions: 'Click an image to send it.',
    initial: 'Find an image or GIF', loading: 'Searching…',
    empty: 'No images found', emptyDetail: 'Try a different search.',
    error: 'Search could not load', errorDetail: 'Try again in a moment.',
    limited: 'The search provider has limited requests', limitedDetail: 'The request received HTTP 429. Try again later.',
    retryAfter: seconds => 'Wait ' + seconds + ' s before searching again',
    unusual: 'The provider detected unusual traffic', unusualDetail: 'Search is temporarily paused.',
    windowTitle: 'Image gallery', closeGallery: 'Close image gallery',
    consent: 'The provider requires confirmation', consentDetail: 'The search stopped at the provider’s consent screen.',
    add: 'Add to favorites', saving: 'Saving…', saved: 'In favorites', failed: 'Try again',
    saveError: 'This image could not be downloaded. Try again or choose another result.'
  } : {
    local: 'Favoritos', finder: 'Buscador', clear: 'Vaciar', cancel: 'Cancelar',
    receive: on => 'Recibidas: ' + (on ? 'Sí' : 'No'),
    question: '¿Vaciar todas las favoritas?',
    purgeCache: 'Purgar caché de imágenes',
    purgeQuestion: '¿Purgar las imágenes en caché del chat y del Buscador? Se conservan los favoritos.',
    confirmPurge: size => 'Sí, purgar la caché (' + size + ')',
    cacheSizing: 'Calculando tamaño…', cacheSizeFailed: 'No se pudo calcular el tamaño de la caché',
    cacheEmpty: 'No hay imágenes temporales que purgar',
    cachePurged: size => 'Caché purgada (' + size + ' liberados)', cacheFailed: 'No se pudo purgar la caché de imágenes',
    previous: 'Anterior', next: 'Siguiente', page: (n, total) => 'Página ' + n + ' / ' + total,
    searchPage: n => 'Página ' + n, loadingMore: 'Buscando más imágenes…',
    moreError: 'No se pudieron cargar más imágenes. Vuelve a intentarlo.',
    localEmpty: 'Todavía no tienes favoritas', localHelp: 'Busca imágenes o GIF para añadirlos a favoritas.',
    sendImage: 'Enviar imagen al chat', sendFailed: 'No se pudo enviar', unavailable: 'No disponible',
    removeImage: 'Quitar de favoritas', removeFailed: 'No se pudo borrar', clearSearch: 'Borrar texto de búsqueda',
    history: 'Búsquedas guardadas', removeSearch: term => 'Borrar búsqueda: ' + term,
    search: 'Buscar', query: 'Busca imágenes o GIF',
    instructions: 'Pulsa una imagen para enviarla al chat.',
    initial: 'Encuentra una imagen o un GIF', loading: 'Buscando…',
    empty: 'No se encontraron imágenes', emptyDetail: 'Prueba con otra búsqueda.',
    error: 'No se pudo cargar la búsqueda', errorDetail: 'Vuelve a intentarlo en un momento.',
    limited: 'El proveedor ha limitado las búsquedas', limitedDetail: 'La consulta recibió HTTP 429. Vuelve a intentarlo más tarde.',
    retryAfter: seconds => 'Espera ' + seconds + ' s antes de buscar de nuevo',
    unusual: 'El proveedor detectó tráfico inusual', unusualDetail: 'La búsqueda se ha pausado temporalmente.',
    windowTitle: 'Galería de imágenes', closeGallery: 'Cerrar galería de imágenes',
    consent: 'El proveedor pide confirmación', consentDetail: 'La consulta se detuvo en la pantalla de consentimiento.',
    add: 'Añadir a favoritas', saving: 'Guardando…', saved: 'En favoritas', failed: 'Reintentar',
    saveError: 'No se pudo descargar esta imagen. Reintenta o elige otro resultado.'
  };

  document.documentElement.lang = english ? 'en' : 'es';
  const el = id => document.getElementById(id);
  const input = el('query');
  const submit = el('submit');
  const results = el('results');
  const localResults = el('local-results');
  const localTab = el('local-tab');
  const clearSearch = el('clear-search');
  const searchView = el('search-view');
  const searchPrevious = el('search-previous');
  const searchNext = el('search-next');
  const historyTabs = el('search-history');
  const purgeCache = el('purge-search-cache');
  const purgeConfirm = el('search-cache-confirm');
  const purgeConfirmButton = el('confirm-search-cache');
  const purgeStatus = el('search-cache-status');
  const sizeFormat = new Intl.NumberFormat(english ? 'en' : 'es', {maximumFractionDigits: 2});
  const pageSize = 12;
  const searchPages = new Map();
  let searchTabs = [];
  let searching = false;
  let loadingMore = null;
  let providerWaitUntil = 0;
  let providerWaitTimer = null;
  let searchSequence = 0;
  let viewVersion = -1;
  let searchResult = null;
  let page = 0;
  let gallery;
  let drawnRevision;
  let refreshSequence = 0;
  let purgeSizeRequest = 0;
  let purgeStatusTimer;

  function query(request) {
    return new Promise((resolve, reject) => window.cefQuery({
      request, onSuccess: resolve, onFailure: (code, reason) => reject({code, reason})
    }));
  }

  function providerMessage(error) {
    const wait = error?.code === 429 ? error.reason?.match(/^(unusual-traffic|rate-limit|retry-after):(\d+)$/) : null;
    if (wait) pauseProvider(Number(wait[2]));
    if (wait?.[1] === 'unusual-traffic' || error?.reason === 'unusual-traffic') {
      return [copy.unusual, copy.unusualDetail];
    }
    if (error?.code === 429) return [copy.limited, copy.limitedDetail];
    if (error?.code === 412) return [copy.consent, copy.consentDetail];
    return null;
  }

  function providerWait() {
    return Math.max(0, Math.ceil((providerWaitUntil - Date.now()) / 1000));
  }

  function pauseProvider(seconds) {
    if (!Number.isFinite(seconds) || seconds <= 0) return;
    providerWaitUntil = Math.max(providerWaitUntil, Date.now() + seconds * 1000);
    if (providerWaitTimer === null) providerWaitTimer = setInterval(updateSearchControls, 1000);
    updateSearchControls();
  }

  function showSearch(show) {
    localTab.setAttribute('aria-pressed', !show);
    el('search-tab').setAttribute('aria-pressed', show);
    el('local').hidden = show;
    el('local-controls').hidden = show;
    el('search-controls').hidden = !show;
    searchView.hidden = !show;
    el('clear-confirm').hidden = true;
    closePurgeConfirmation();
    if (show) {
      input.focus();
      refreshSavedResults();
    } else refreshLocalGallery();
  }

  function searchStatus(title, detail = '') {
    el('status-title').textContent = title;
    el('status-detail').textContent = detail;
    el('status').hidden = false;
    results.hidden = true;
    el('search-pages').hidden = true;
  }

  function closePurgeConfirmation() {
    purgeSizeRequest++;
    purgeConfirm.hidden = true;
  }

  function formatCacheSize(bytes) {
    if (bytes > 0 && bytes < 10_000) return '<' + sizeFormat.format(0.01) + ' MB';
    return sizeFormat.format(bytes / 1_000_000) + ' MB';
  }

  function setPurgeStatus(message, dismiss = false) {
    clearTimeout(purgeStatusTimer);
    purgeStatus.textContent = message;
    if (dismiss) {
      purgeStatusTimer = setTimeout(() => { purgeStatus.textContent = ''; }, 4_000);
    }
  }

  function imagePreview() {
    const frame = document.createElement('div');
    const send = document.createElement('button');
    const preview = document.createElement('div');
    const image = document.createElement('img');
    frame.className = 'media-frame';
    send.type = 'button';
    send.className = 'media-send';
    send.setAttribute('aria-label', copy.sendImage);
    preview.className = 'preview';
    image.alt = '';
    image.decoding = 'async';
    image.addEventListener('error', () => image.remove(), {once: true});
    const fallback = el('local-empty').querySelector('svg').cloneNode(true);
    preview.append(fallback, image);
    send.append(preview);
    frame.append(send);
    return {frame, send, preview, image};
  }

  function imageStatus(preview) {
    const status = document.createElement('span');
    status.className = 'image-status';
    status.setAttribute('role', 'status');
    status.hidden = true;
    preview.append(status);
    return status;
  }

  function fitImage(image, frame, ratioProperty = '--preview-ratio') {
    image.addEventListener('load', () => {
      frame.style.setProperty(ratioProperty, image.naturalWidth / image.naturalHeight);
    }, {once: true});
  }

  const searchPreviews = new IntersectionObserver((entries, observer) => {
    for (const entry of entries) {
      if (!entry.isIntersecting || !entry.target.isConnected) continue;
      observer.unobserve(entry.target);
      const image = entry.target.querySelector('img[data-thumbnail]');
      if (!image) continue;
      image.src = image.dataset.thumbnail;
      delete image.dataset.thumbnail;
    }
  }, {root: el('content')});

  const searchAnimations = new IntersectionObserver(entries => {
    for (const entry of entries) {
      entry.target.showAnimation(entry.target.isConnected && entry.isIntersecting &&
        entry.intersectionRatio > .05);
    }
  }, {root: el('content'), threshold: .05});

  const pendingAnimations = new Set();
  const maxConcurrentAnimations = 2;
  let activeAnimations = 0;

  function loadVisibleAnimations() {
    while (!searchView.hidden && activeAnimations < maxConcurrentAnimations && pendingAnimations.size) {
      const start = pendingAnimations.values().next().value;
      pendingAnimations.delete(start);
      start();
    }
  }

  function animatedPreview(card, frame, preview, batch, index) {
    const motion = document.createElement('img');
    motion.className = 'motion';
    motion.alt = '';
    motion.decoding = 'async';
    motion.hidden = true;
    fitImage(motion, frame, '--animation-ratio');
    preview.append(motion);
    let visible = false;
    let ready = false;
    let failed = false;
    let loading = false;
    let timer;
    const start = () => {
      if (!card.isConnected || !visible) return;
      loading = true;
      activeAnimations++;
      motion.src = 'search-preview/' + batch + '/' + index;
    };
    const finish = success => {
      if (!loading) return;
      loading = false;
      activeAnimations--;
      ready = success;
      failed = !success;
      motion.hidden = !ready || !visible;
      loadVisibleAnimations();
    };
    motion.addEventListener('load', () => finish(true));
    motion.addEventListener('error', () => finish(false));
    card.showAnimation = next => {
      visible = next;
      clearTimeout(timer);
      if (!next) {
        motion.hidden = true;
        pendingAnimations.delete(start);
      } else if (!failed) {
        // Wait for the row to settle and let started downloads finish for reuse.
        timer = setTimeout(() => {
          if (!card.isConnected || !visible) return;
          if (ready) motion.hidden = false;
          else if (!loading) {
            pendingAnimations.add(start);
            loadVisibleAnimations();
          }
        }, 150);
      }
    };
    card.disposeAnimation = () => {
      clearTimeout(timer);
      pendingAnimations.delete(start);
      if (loading) {
        loading = false;
        activeAnimations--;
        motion.removeAttribute('src');
      }
    };
  }

  function favoriteLabel(button, label) {
    button.title = label;
    button.setAttribute('aria-label', label);
  }

  function setSaved(card, saved) {
    const button = card.querySelector('.save-image');
    card.classList.toggle('saved', saved);
    button.disabled = saved;
    button.setAttribute('aria-pressed', saved);
    favoriteLabel(button, saved ? copy.saved : copy.add);
  }

  async function refreshSavedResults() {
    const current = searchResult;
    if (!current?.images.length) return;
    try {
      const states = JSON.parse(await query('saved\t' + current.batch + '\t' + current.page));
      if (searchResult !== current) return;
      updateSavedCards(searchPages.get(current.batch + ':' + current.page), states);
    } catch {
      // The query can finish after the result grid has been replaced.
    }
  }

  function updateSavedCards(grid, states) {
    states.forEach((saved, index) => {
      const card = grid?.children[index];
      if (card && !card.classList.contains('saving') && !card.classList.contains('sending') &&
          card.classList.contains('saved') !== saved) setSaved(card, saved);
    });
  }

  function searchCard(result, batch, index) {
    const card = document.createElement('article');
    const {frame, send, preview, image} = imagePreview();
    const button = document.createElement('button');
    const status = imageStatus(preview);
    const key = '\t' + batch + '\t' + index;
    card.className = 'media-tile search-card';
    button.type = 'button';
    button.className = 'media-action save-image icon-button';
    button.append(el('favorite-star').content.cloneNode(true));
    frame.append(button);
    card.append(frame);
    setSaved(card, result.saved);
    fitImage(image, frame);
    image.dataset.thumbnail = result.thumbnail;
    if (result.animated) animatedPreview(card, frame, preview, batch, index);
    button.addEventListener('click', async () => {
      button.disabled = true;
      send.disabled = true;
      card.classList.remove('failed');
      card.classList.add('saving');
      favoriteLabel(button, copy.saving);
      try {
        const response = await query('save' + key);
        if (response === 'added') {
          page = 0;
        }
        setSaved(card, true);
      } catch {
        card.classList.add('failed');
        button.setAttribute('aria-label', copy.failed);
        button.title = copy.saveError;
        button.disabled = false;
      } finally {
        card.classList.remove('saving');
        send.disabled = false;
      }
    });
    send.addEventListener('click', async () => {
      const wasDisabled = button.disabled;
      status.hidden = true;
      send.disabled = true;
      button.disabled = true;
      card.classList.add('sending');
      try {
        await query('send-result' + key);
      } catch {
        status.textContent = copy.sendFailed;
        status.hidden = false;
      } finally {
        card.classList.remove('sending');
        send.disabled = false;
        button.disabled = wasDisabled;
      }
    });
    return card;
  }

  function localCard(revision, index, observer) {
    const item = document.createElement('div');
    const remove = document.createElement('button');
    const {frame, send, preview, image} = imagePreview();
    const status = imageStatus(preview);
    const key = '\t' + revision + '\t' + index;
    item.className = 'media-tile';
    send.classList.add('local-card');
    remove.type = 'button';
    remove.className = 'media-action remove-image icon-button danger';
    remove.setAttribute('aria-label', copy.removeImage);
    remove.title = copy.removeImage;
    remove.append(clearSearch.querySelector('svg').cloneNode(true));
    frame.append(remove);
    item.append(frame);
    send.disabled = true;
    fitImage(image, frame);
    image.addEventListener('load', () => { send.disabled = false; }, {once: true});
    image.addEventListener('error', () => {
      status.textContent = copy.unavailable;
      status.hidden = false;
    }, {once: true});
    item.loadPreview = () => {
      image.src = 'preview/' + revision + '/' + index;
    };
    observer.observe(item);
    send.addEventListener('click', async () => {
      send.disabled = true;
      status.hidden = true;
      try {
        await query('send' + key);
      } catch {
        status.textContent = copy.sendFailed;
        status.hidden = false;
      } finally {
        send.disabled = false;
      }
    });
    remove.addEventListener('click', async () => {
      remove.disabled = true;
      try {
        await query('remove' + key);
        refreshLocalGallery();
      } catch {
        status.textContent = copy.removeFailed;
        status.hidden = false;
        remove.disabled = false;
      }
    });
    return item;
  }

  let previewObserver;
  function drawLocal() {
    if (!gallery) return;
    drawnRevision = gallery.revision;
    const total = Math.max(1, Math.ceil(gallery.size / pageSize));
    page = Math.min(total - 1, page);
    const start = page * pageSize;
    el('receive').textContent = copy.receive(gallery.autoReceive);
    el('receive').setAttribute('aria-pressed', gallery.autoReceive);
    el('clear').disabled = !gallery.size;
    el('local-empty').hidden = !!gallery.size;
    localResults.hidden = !gallery.size;
    el('pages').hidden = total < 2;
    el('page').textContent = copy.page(page + 1, total);
    el('previous').disabled = page === 0;
    el('next').disabled = page + 1 === total;
    previewObserver?.disconnect();
    previewObserver = new IntersectionObserver((entries, observer) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        observer.unobserve(entry.target);
        entry.target.loadPreview();
      }
    }, {root: el('local-content')});
    const visible = Math.min(pageSize, gallery.size - start);
    localResults.replaceChildren(...Array.from({length: visible}, (_, i) =>
      localCard(gallery.revision, start + i, previewObserver)));
    el('local-content').scrollTop = 0;
  }

  async function refreshLocalGallery() {
    const sequence = ++refreshSequence;
    try {
      const next = JSON.parse(await query('gallery'));
      if (sequence !== refreshSequence) return;
      gallery = next;
      localTab.textContent = `${copy.local} (${next.size})`;
      if (el('local').hidden) return;
      if (drawnRevision !== next.revision) drawLocal();
      else {
        el('receive').textContent = copy.receive(next.autoReceive);
        el('receive').setAttribute('aria-pressed', next.autoReceive);
      }
    } catch {
      // Closing the dialog also cancels outstanding queries.
    }
  }
  window.refreshLocalGallery = refreshLocalGallery;

  function detachSearchGrid() {
    searchPreviews.disconnect();
    searchAnimations.disconnect();
    for (const card of results.firstElementChild?.children ?? []) card.showAnimation?.(false);
    results.replaceChildren();
  }

  function releaseSearchGrid(key, grid) {
    if (results.firstElementChild === grid) detachSearchGrid();
    for (const card of grid.children) card.disposeAnimation?.();
    searchPages.delete(key);
  }

  function drawSearchTabs(tabs, active, changed) {
    if (tabs.length !== searchTabs.length || tabs.some((tab, index) => tab.batch !== searchTabs[index].batch)) {
      historyTabs.replaceChildren(...tabs.map(tab => {
        const item = document.createElement('div');
        const title = document.createElement('button');
        const remove = document.createElement('button');
        item.className = 'history-tab';
        item.dataset.batch = tab.batch;
        title.type = remove.type = 'button';
        title.className = 'history-title';
        title.title = title.textContent = tab.term;
        title.addEventListener('click', () => {
          searchSequence++;
          readSearchView('search-select\t' + tab.batch, true);
        });
        remove.className = 'history-remove icon-button danger';
        remove.title = copy.removeSearch(tab.term);
        remove.setAttribute('aria-label', remove.title);
        const cross = clearSearch.querySelector('svg').cloneNode(true);
        cross.setAttribute('aria-hidden', 'true');
        remove.append(cross);
        remove.addEventListener('click', () => {
          const deletingActive = searchResult?.batch === tab.batch;
          if (deletingActive) searchSequence++;
          readSearchView('search-delete\t' + tab.batch, deletingActive);
        });
        item.append(title, remove);
        return item;
      }));
    }
    searchTabs = tabs;
    historyTabs.hidden = !tabs.length;
    for (const tab of historyTabs.children) {
      const selected = Number(tab.dataset.batch) === active;
      tab.firstElementChild.setAttribute('aria-pressed', selected);
      if (selected && changed) tab.scrollIntoView({block: 'nearest', inline: 'nearest'});
    }
  }

  function applySearchView(view, restoreInput) {
    pauseProvider(view.retryAfter);
    if (view.version < viewVersion) return;
    viewVersion = view.version;
    const oldBatch = searchResult?.batch;
    searchResult = view.result ?? null;
    if (restoreInput) {
      input.value = searchResult?.term ?? '';
      clearSearch.hidden = !input.value;
    }
    const open = new Set(view.tabs.map(tab => tab.batch));
    for (const [key, grid] of searchPages) {
      if (!open.has(Number(grid.dataset.batch))) releaseSearchGrid(key, grid);
    }
    drawSearchTabs(view.tabs, searchResult?.batch, oldBatch !== searchResult?.batch);
    if (searchResult?.images.length) {
      el('status').hidden = true;
      results.hidden = false;
      drawSearchPage();
    } else {
      detachSearchGrid();
      if (!searchResult) searchStatus(copy.initial);
      else if (searchResult.hasMore) searchStatus(copy.error, copy.errorDetail);
      else searchStatus(copy.empty, copy.emptyDetail);
    }
  }

  async function readSearchView(action, restoreInput = false) {
    try {
      applySearchView(JSON.parse(await query(action)), restoreInput);
    } catch {
      // A tab or the gallery may have closed before the local request finished.
    }
  }

  function drawSearchPage() {
    const {batch, page: index, images, firstIndex} = searchResult;
    const key = batch + ':' + index;
    let grid = searchPages.get(key);
    if (!grid) {
      grid = document.createElement('div');
      grid.className = 'grid';
      grid.dataset.batch = batch;
      grid.append(...images.map((image, offset) => searchCard(image, batch, firstIndex + offset)));
    } else updateSavedCards(grid, images.map(image => image.saved));

    if (results.firstElementChild !== grid) {
      detachSearchGrid();
      results.replaceChildren(grid);
      el('content').scrollTop = 0;
      for (const card of grid.children) {
        if (card.querySelector('img[data-thumbnail]')) searchPreviews.observe(card);
        if (card.showAnimation) searchAnimations.observe(card);
      }
    }
    // Keep recent page elements while leaving the full search history on disk.
    searchPages.delete(key);
    searchPages.set(key, grid);
    if (searchPages.size > 3) {
      const [oldest, visited] = searchPages.entries().next().value;
      releaseSearchGrid(oldest, visited);
    }
    updateSearchPager();
  }

  function updateSearchPager() {
    const {batch, page: index, pageCount, hasMore} = searchResult;
    const loading = loadingMore === batch;
    const cachedNext = index + 1 < pageCount;
    const focused = document.activeElement;
    el('search-pages').hidden = pageCount < 2 && !hasMore;
    el('search-page').textContent = loading ? copy.loadingMore
      : hasMore ? copy.searchPage(index + 1) : copy.page(index + 1, pageCount);
    searchPrevious.disabled = loading || index === 0;
    searchNext.disabled = loading || (!cachedNext
      && (!hasMore || searching || loadingMore !== null || providerWait() > 0));
    if (loading) return;
    if (focused === searchPrevious && searchPrevious.disabled) searchNext.focus();
    else if (focused === searchNext && searchNext.disabled) searchPrevious.focus();
  }

  function updateSearchControls() {
    const seconds = providerWait();
    submit.disabled = searching || loadingMore !== null || seconds > 0;
    el('instructions').hidden = seconds > 0;
    el('provider-wait').hidden = seconds === 0;
    const message = seconds > 0 ? copy.retryAfter(seconds) : '';
    if (el('provider-wait').textContent !== message) el('provider-wait').textContent = message;
    if (seconds === 0 && providerWaitTimer !== null) {
      clearInterval(providerWaitTimer);
      providerWaitTimer = null;
    }
    if (searchResult?.images.length && !results.hidden) updateSearchPager();
  }

  async function moveSearchPage(offset) {
    const current = searchResult;
    if (!current || loadingMore === current.batch) return;
    const next = current.page + offset;
    if (next < 0) return;
    if (next < current.pageCount) {
      searchSequence++;
      return readSearchView('search-page\t' + current.batch + '\t' + next);
    }
    if (!current.hasMore || searching || loadingMore !== null || providerWait() > 0) return;
    loadingMore = current.batch;
    updateSearchControls();
    let error;
    try {
      const view = JSON.parse(await query('search-more\t' + current.batch + '\t' + current.page));
      applySearchView(view, false);
    } catch (failure) {
      error = failure;
    } finally {
      loadingMore = null;
      const message = providerMessage(error);
      updateSearchControls();
      if (searchResult?.batch === current.batch && error !== undefined && error.code !== 499) {
        el('search-page').textContent = message?.[0] ?? copy.moreError;
      }
    }
  }

  el('search').addEventListener('submit', async event => {
    event.preventDefault();
    const term = input.value.trim();
    if (!term || searching || loadingMore !== null || providerWait() > 0) return;
    const sequence = ++searchSequence;
    searching = true;
    updateSearchControls();
    searchStatus(copy.loading);
    try {
      const view = JSON.parse(await query('search\t' + term));
      applySearchView(view, sequence === searchSequence && input.value.trim() === term);
    } catch (error) {
      const message = providerMessage(error);
      if (sequence === searchSequence) searchStatus(...(message ?? [copy.error, copy.errorDetail]));
    } finally {
      searching = false;
      updateSearchControls();
    }
  });

  el('window-title').textContent = copy.windowTitle;
  el('close-gallery').setAttribute('aria-label', copy.closeGallery);
  el('close-gallery').title = copy.closeGallery;
  el('close-gallery').addEventListener('click', () => query('close-window').catch(() => {}));

  for (const [id, label] of Object.entries({
    'local-tab': copy.local, 'search-tab': copy.finder, clear: copy.clear,
    'clear-question': copy.question, 'confirm-clear': copy.clear, 'cancel-clear': copy.cancel,
    'purge-search-cache': copy.purgeCache, 'search-cache-question': copy.purgeQuestion,
    'confirm-search-cache': copy.cacheSizing, 'cancel-search-cache': copy.cancel,
    previous: copy.previous, next: copy.next, 'search-previous': copy.previous, 'search-next': copy.next,
    'local-title': copy.localEmpty,
    'local-help': copy.localHelp, instructions: copy.instructions, submit: copy.search
  })) el(id).textContent = label;
  input.placeholder = copy.query;
  input.setAttribute('aria-label', copy.query);
  clearSearch.title = copy.clearSearch;
  clearSearch.setAttribute('aria-label', copy.clearSearch);
  historyTabs.setAttribute('aria-label', copy.history);
  input.addEventListener('input', () => {
    clearSearch.hidden = !input.value;
  });
  clearSearch.addEventListener('click', () => {
    input.value = '';
    clearSearch.hidden = true;
    input.focus();
  });
  localTab.addEventListener('click', () => showSearch(false));
  el('search-tab').addEventListener('click', () => showSearch(true));
  el('previous').addEventListener('click', () => { page--; drawLocal(); });
  el('next').addEventListener('click', () => { page++; drawLocal(); });
  searchPrevious.addEventListener('click', () => moveSearchPage(-1));
  searchNext.addEventListener('click', () => moveSearchPage(1));
  purgeCache.addEventListener('click', async () => {
    const request = ++purgeSizeRequest;
    setPurgeStatus('');
    purgeConfirmButton.textContent = copy.cacheSizing;
    purgeConfirmButton.disabled = true;
    purgeConfirm.hidden = false;
    el('cancel-search-cache').focus();
    try {
      const bytes = Number(await query('image-cache-size'));
      if (request !== purgeSizeRequest) return;
      if (!bytes) {
        closePurgeConfirmation();
        setPurgeStatus(copy.cacheEmpty, true);
        purgeCache.focus();
        return;
      }
      purgeConfirmButton.textContent = copy.confirmPurge(formatCacheSize(bytes));
      purgeConfirmButton.disabled = false;
    } catch {
      if (request !== purgeSizeRequest) return;
      closePurgeConfirmation();
      setPurgeStatus(copy.cacheSizeFailed);
      purgeCache.focus();
    }
  });
  el('cancel-search-cache').addEventListener('click', () => {
    closePurgeConfirmation();
    purgeCache.focus();
  });
  purgeConfirmButton.addEventListener('click', async () => {
    closePurgeConfirmation();
    purgeCache.disabled = true;
    try {
      const bytes = Number(await query('purge-image-cache'));
      setPurgeStatus(bytes ? copy.cachePurged(formatCacheSize(bytes)) : copy.cacheEmpty, true);
    } catch {
      setPurgeStatus(copy.cacheFailed);
    } finally {
      purgeCache.disabled = false;
      if (!searchView.hidden) purgeCache.focus();
    }
  });
  el('receive').addEventListener('click', () => query('receive').catch(() => {}));
  el('clear').addEventListener('click', () => { el('clear-confirm').hidden = false; });
  el('cancel-clear').addEventListener('click', () => { el('clear-confirm').hidden = true; });
  el('confirm-clear').addEventListener('click', async () => {
    el('clear-confirm').hidden = true;
    await query('clear');
  });
  searchStatus(copy.initial);
  query('ready').then(() => {
    refreshLocalGallery();
    return readSearchView('search-state', !input.value);
  }).catch(() => searchStatus(copy.error, copy.errorDetail));
})();
