/* Copyright (C) 2026 tonikelope; GPLv3 or later. */
(() => {
  'use strict';

  function viewportPosition(x, y, width, height) {
    const margin = 12;
    const gap = 16;
    const maxX = Math.max(margin, innerWidth - width - margin);
    const maxY = Math.max(margin, innerHeight - height - margin);
    const below = y + gap;
    const top = below <= maxY ? below : y - height - gap;
    return [Math.max(margin, Math.min(x + gap, maxX)),
      Math.max(margin, Math.min(top, maxY))];
  }

  window.coronaTooltipPosition = viewportPosition;
  const tip = document.createElement('div');
  tip.className = 'ui-tooltip';
  tip.setAttribute('role', 'tooltip');
  tip.hidden = true;
  document.body.append(tip);
  let target, timer, x, y;

  function hide() {
    clearTimeout(timer);
    target = null;
    tip.hidden = true;
  }

  function show() {
    if (!target?.isConnected || !target.getClientRects().length || !target.dataset.tooltip) {
      hide();
      return;
    }
    tip.textContent = target.dataset.tooltip;
    tip.hidden = false;
    const zoom = Number(getComputedStyle(document.body).zoom) || 1;
    tip.style.maxWidth = `min(30rem, ${(innerWidth - 24) / zoom}px)`;
    const {width, height} = tip.getBoundingClientRect();
    const [left, top] = viewportPosition(x, y, width, height);
    tip.style.left = left / zoom + 'px';
    tip.style.top = top / zoom + 'px';
  }

  function select(next, clientX, clientY) {
    x = clientX;
    y = clientY;
    if (target === next) return;
    hide();
    if (!next?.dataset.tooltip) return;
    target = next;
    timer = setTimeout(show, 500);
  }

  document.addEventListener('pointermove', event => {
    if (event.pointerType === 'touch') return;
    select(event.target.closest('[data-tooltip]'), event.clientX, event.clientY);
  });
  document.addEventListener('pointerout', event => {
    if (!event.relatedTarget) hide();
  });
  document.addEventListener('focusin', event => {
    const next = event.target.closest('[data-tooltip]');
    if (!next || !event.target.matches(':focus-visible')) return;
    const rect = next.getBoundingClientRect();
    select(next, rect.left + rect.width / 2, rect.bottom);
  });
  document.addEventListener('focusout', hide);
  document.addEventListener('pointerdown', hide);
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape') hide();
  });
  document.addEventListener('scroll', hide, {capture:true, passive:true});
  window.addEventListener('resize', hide);
  window.addEventListener('blur', hide);
  new MutationObserver(() => {
    if (!target) return;
    if (!target.isConnected || !target.getClientRects().length) hide();
    else if (!tip.hidden && tip.textContent !== target.dataset.tooltip) show();
  }).observe(document.body, {subtree:true, childList:true, attributes:true,
    attributeFilter:['data-tooltip', 'hidden']});
})();
