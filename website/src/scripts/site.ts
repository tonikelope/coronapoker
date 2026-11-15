const mobileMenu = document.querySelector<HTMLDetailsElement>('.mobile-nav');
mobileMenu?.querySelector('nav')?.addEventListener('click', event => {
  if (event.target instanceof HTMLAnchorElement) mobileMenu.open = false;
});
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && mobileMenu?.open) {
    mobileMenu.open = false;
    mobileMenu.querySelector('summary')?.focus();
  }
});
document.addEventListener('pointerdown', event => {
  if (mobileMenu?.open && event.target instanceof Node && !mobileMenu.contains(event.target)) {
    mobileMenu.open = false;
  }
});

const protocol = document.querySelector('[data-mental]');
if (protocol) {
  const controls = protocol.querySelector<HTMLElement>('.protocol-controls')!;
  const buttons = [...controls.querySelectorAll<HTMLButtonElement>('[data-mental-tab]')];
  const stages = [...protocol.querySelectorAll<HTMLElement>('[data-mental-stage]')];
  const next = [...protocol.querySelectorAll<HTMLButtonElement>('[data-mental-next]')];

  function showStep(index: number) {
    buttons.forEach((button, position) => button.setAttribute('aria-pressed', String(index === position)));
    stages.forEach((stage, position) => {
      if (index !== position && !stage.hidden) stage.dispatchEvent(new Event('mental-stage-leave'));
      stage.hidden = index !== position;
    });
  }

  buttons.forEach((button, index) => button.addEventListener('click', () => showStep(index)));
  next.forEach(button => {
    button.hidden = false;
    button.addEventListener('click', () => {
      const index = Number(button.dataset.mentalNext);
      showStep(index);
      buttons[index].focus();
    });
  });
  showStep(0);
  controls.hidden = false;
}

const shuffle = document.querySelector<HTMLElement>('[data-shuffle]');
if (shuffle) {
  const deck = shuffle.querySelector<HTMLElement>('[data-shuffle-cards]')!;
  const timeline = shuffle.querySelector<HTMLElement>('[data-shuffle-frames]')!;
  const frames = [...timeline.querySelectorAll('li')];
  const players = [...shuffle.querySelectorAll('[data-shuffle-players] li')];
  const legend = shuffle.querySelector<HTMLElement>('[data-shuffle-legend]')!;
  const play = shuffle.querySelector<HTMLButtonElement>('[data-shuffle-play]')!;
  const status = shuffle.querySelector('[data-shuffle-status]')!;
  const motion = matchMedia('(prefers-reduced-motion: reduce)');
  const privateSlots = players.length * 2;
  const boardSlots = ['burn', 'flop', 'flop', 'flop', 'burn', 'turn', 'burn', 'river'];
  let completed = 0;
  let interrupted = false;

  shuffle.closest('[data-mental-stage]')?.addEventListener('mental-stage-leave', () => {
    interrupted = true;
    deck.getAnimations?.({ subtree: true }).forEach(animation => animation.cancel());
  });

  function showShuffleState() {
    const isChangingLocks = completed >= players.length;
    const changed = isChangingLocks ? completed - players.length : 0;
    const turn = isChangingLocks ? changed : completed;
    deck.dataset.locks = String(Math.min(completed, players.length));
    deck.dataset.phase = 'ready';
    shuffle.toggleAttribute('data-community-phase', isChangingLocks);
    legend.hidden = !isChangingLocks;
    [...deck.children].forEach((card, position) => {
      const community = isChangingLocks && position >= privateSlots;
      card.classList.toggle('community-slot', community);
      const boardSlot = community ? boardSlots[position - privateSlots] : undefined;
      if (boardSlot) card.setAttribute('data-board-slot', boardSlot);
      else card.removeAttribute('data-board-slot');
      [...card.children].forEach((lock, owner) => {
        lock.classList.toggle('community-lock', community && owner < changed);
      });
    });
    status.textContent = frames[completed].textContent;
    play.textContent = frames[completed].dataset.play ?? timeline.dataset.shuffleReplay!;
    players.forEach((player, index) => {
      player.classList.toggle('is-complete', index < turn);
      if (index === turn) player.setAttribute('aria-current', 'step');
      else player.removeAttribute('aria-current');
    });
  }

  async function mixDeck(cards: HTMLElement[], animated: boolean) {
    status.textContent = frames[completed].dataset.locking!;
    deck.dataset.phase = 'locking';
    deck.dataset.locks = String(completed + 1);
    if (animated) {
      const seals = cards.map((card, index) => card.children[completed].animate([
        { opacity: 0, transform: 'translateY(-4px) scale(.75)' },
        { opacity: 1, transform: 'none' },
      ], { duration: 420, delay: (index % 9) * 12, easing: 'ease-out', fill: 'backwards' }));
      await Promise.allSettled(seals.map(animation => animation.finished));
    } else {
      await new Promise(resolve => window.setTimeout(resolve, 500));
    }

    status.textContent = frames[completed].dataset.mixing!;
    deck.dataset.phase = 'shuffling';
    const order = [...cards];
    for (let index = order.length - 1; index > 0; index--) {
      const other = Math.floor(Math.random() * (index + 1));
      [order[index], order[other]] = [order[other], order[index]];
    }
    if (!animated || interrupted) {
      deck.append(...order);
    } else {
      const start = cards.map(card => card.getBoundingClientRect());
      deck.append(...order);
      const end = cards.map(card => card.getBoundingClientRect());
      const animations = cards.map((card, index) => {
        const x = start[index].left - end[index].left;
        const y = start[index].top - end[index].top;
        const turn = index % 2 === 0 ? -8 : 8;
        return card.animate([
          { transform: `translate(${x}px, ${y}px)` },
          { transform: `translate(${x * .44}px, ${y * .44 - 9}px) rotate(${turn}deg)` },
          { transform: 'none' },
        ], { duration: 650, delay: (index % 9) * 19, easing: 'ease-in-out', fill: 'backwards' });
      });
      await Promise.allSettled(animations.map(animation => animation.finished));
    }
  }

  async function replaceCommunityLocks(cards: HTMLElement[], animated: boolean) {
    const frame = frames[completed];
    status.textContent = frame.dataset.removeLock!;
    deck.dataset.phase = 'removing-locks';
    if (!animated) return;

    const owner = completed - players.length;
    const locks = cards.slice(privateSlots).map(card => card.children[owner]);
    const originals = locks.map((lock, index) => lock.animate([
      { opacity: 1 }, { opacity: 0, transform: 'translateY(-3px)' },
    ], { duration: 230, delay: index < boardSlots.length ? index * 26 : 0, fill: 'forwards' }));
    await Promise.allSettled(originals.map(animation => animation.finished));
    originals.forEach(animation => animation.cancel());
    if (interrupted) return;
    status.textContent = frame.dataset.replaceLock!;
    deck.dataset.phase = 'replacing-locks';
    locks.forEach(lock => lock.classList.add('community-lock'));
    const replacements = locks.map((lock, index) => lock.animate([
      { opacity: 0, transform: 'translateY(-3px)' }, { opacity: 1, transform: 'none' },
    ], { duration: 360, delay: index < boardSlots.length ? index * 26 : 0, easing: 'ease-out', fill: 'backwards' }));
    await Promise.allSettled(replacements.map(animation => animation.finished));
  }

  play.addEventListener('click', async () => {
    if (play.hasAttribute('aria-disabled')) return;
    if (completed === frames.length - 1) {
      completed = 0;
      showShuffleState();
      return;
    }
    // The cards in the illustration have no faces or attached values.
    const cards = [...deck.children] as HTMLElement[];
    const animated = !motion.matches && typeof deck.animate === 'function';
    interrupted = false;
    play.setAttribute('aria-disabled', 'true');
    if (completed < players.length) await mixDeck(cards, animated);
    else await replaceCommunityLocks(cards, animated);
    completed++;
    showShuffleState();
    play.removeAttribute('aria-disabled');
  });
  showShuffleState();
  timeline.hidden = true;
  shuffle.querySelector<HTMLElement>('[data-shuffle-controls]')!.hidden = false;
}

const deal = document.querySelector('[data-deal]');
if (deal) {
  const timeline = deal.querySelector<HTMLOListElement>('[data-deal-frames]')!;
  const frames = [...timeline.querySelectorAll('li')];
  const locks = [...deal.querySelectorAll<HTMLElement>('[data-deal-lock]')];
  const groups = [...deal.querySelectorAll<HTMLElement>('[data-deal-group]')];
  const deliveries = [...deal.querySelectorAll<HTMLElement>('[data-deal-delivery]')];
  const back = deal.querySelector<HTMLButtonElement>('[data-deal-back]')!;
  const forward = deal.querySelector<HTMLButtonElement>('[data-deal-forward]')!;
  const action = forward.querySelector('[data-deal-action]')!;
  const arrow = forward.querySelector('[data-deal-arrow]')!;
  const status = deal.querySelector('[data-deal-status]')!;
  let frame = 0;

  function showFrame(index: number) {
    const advancing = index > frame;
    frame = index;
    const finished = frame === frames.length - 1;
    action.textContent = finished ? timeline.dataset.replayLabel! : frames[frame].dataset.action!;
    arrow.textContent = finished ? '↺' : '→';
    back.disabled = frame === 0;
    status.textContent = frames[frame].textContent;

    deliveries.forEach((delivery, position) => {
      const start = Number(delivery.dataset.dealDelivery);
      const next = Number(deliveries[position + 1]?.dataset.dealDelivery ?? frames.length);
      delivery.hidden = frame < start || frame >= next;
      delivery.classList.toggle('just-delivered', advancing && frame === start);
    });
    locks.forEach(lock => {
      const step = Number(lock.dataset.dealLock);
      lock.classList.toggle('just-unlocked', advancing && frame === step);
      lock.querySelector<HTMLElement>('.mini-lock')!.hidden = frame >= step;
      lock.querySelector<HTMLElement>('.deal-unlocked')!.hidden = frame < step;
    });
    groups.forEach(group => {
      const step = Number(group.dataset.dealGroup);
      group.classList.toggle('just-revealed', advancing && frame === step);
      group.querySelectorAll<HTMLElement>('[data-card-face], [data-cards-open]').forEach(node => {
        node.hidden = frame < step;
      });
      group.querySelectorAll<HTMLElement>('[data-card-back], [data-cards-sealed]').forEach(node => {
        node.hidden = frame >= step;
      });
    });
  }

  back.addEventListener('click', () => {
    showFrame(frame - 1);
    if (back.disabled) forward.focus();
  });
  forward.addEventListener('click', () => showFrame((frame + 1) % frames.length));
  deal.closest('[data-mental-stage]')?.addEventListener('mental-stage-leave', () => showFrame(frame));
  showFrame(0);
  timeline.hidden = true;
  deal.querySelectorAll<HTMLElement>('[data-deal-locks], [data-deal-controls], [data-deal-interactive]').forEach(node => {
    node.hidden = false;
  });
}

const featureTabs = document.querySelector<HTMLElement>('[data-feature-tabs]');
if (featureTabs) {
  const tabs = [...featureTabs.querySelectorAll<HTMLButtonElement>('[role="tab"]')];
  const panels = tabs.map(tab => document.getElementById(tab.getAttribute('aria-controls')!)!);

  function showFeature(index: number) {
    tabs.forEach((tab, position) => {
      const selected = index === position;
      tab.setAttribute('aria-selected', String(selected));
      tab.tabIndex = selected ? 0 : -1;
      panels[position].hidden = !selected;
    });
  }

  tabs.forEach((tab, index) => {
    const panel = panels[index];
    panel.setAttribute('role', 'tabpanel');
    panel.setAttribute('aria-labelledby', tab.id);
    panel.tabIndex = 0;
    tab.addEventListener('click', () => showFeature(index));
    tab.addEventListener('keydown', event => {
      let next: number;
      switch (event.key) {
        case 'ArrowLeft': next = (index + tabs.length - 1) % tabs.length; break;
        case 'ArrowRight': next = (index + 1) % tabs.length; break;
        case 'Home': next = 0; break;
        case 'End': next = tabs.length - 1; break;
        default: return;
      }
      event.preventDefault();
      showFeature(next);
      tabs[next].focus();
    });
  });
  showFeature(0);
  featureTabs.hidden = false;
}

const lightbox = document.querySelector<HTMLDialogElement>('.lightbox');
if (lightbox && typeof lightbox.showModal === 'function') {
  const shots = [...document.querySelectorAll<HTMLAnchorElement>('[data-gallery-entry]')];
  const title = lightbox.querySelector('h2')!;
  const image = lightbox.querySelector<HTMLImageElement>('[data-full]')!;
  const original = lightbox.querySelector<HTMLAnchorElement>('[data-original]')!;
  let activeShot = 0;

  function showShot(index: number) {
    activeShot = (index + shots.length) % shots.length;
    const shot = shots[activeShot];
    const preview = shot.querySelector('img')!;
    const caption = shot.closest('figure')?.querySelector('figcaption')?.textContent?.trim();
    title.textContent = `${caption} · ${activeShot + 1} / ${shots.length}`;
    image.alt = preview.alt;
    image.src = shot.href;
    original.href = shot.href;
  }

  document.querySelectorAll<HTMLAnchorElement>('[data-shot]').forEach(link => {
    link.setAttribute('aria-haspopup', 'dialog');
    link.addEventListener('click', event => {
      if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      event.preventDefault();
      showShot(Number(link.dataset.shot));
      lightbox.showModal();
      document.documentElement.classList.add('modal-open');
    });
  });
  lightbox.querySelector('[data-close]')?.addEventListener('click', () => lightbox.close());
  lightbox.querySelector('[data-next]')?.addEventListener('click', () => showShot(activeShot + 1));
  lightbox.querySelector('[data-previous]')?.addEventListener('click', () => showShot(activeShot - 1));
  lightbox.addEventListener('click', event => {
    if (event.target === lightbox) lightbox.close();
  });
  lightbox.addEventListener('keydown', event => {
    if (event.key === 'ArrowLeft') showShot(activeShot - 1);
    if (event.key === 'ArrowRight') showShot(activeShot + 1);
  });
  lightbox.addEventListener('close', () => document.documentElement.classList.remove('modal-open'));
}

if ('IntersectionObserver' in window && matchMedia('(prefers-reduced-motion: no-preference)').matches) {
  const observer = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (!entry.isIntersecting) return;
      entry.target.classList.add('arrived');
      observer.unobserve(entry.target);
    });
  });
  document.querySelectorAll('.reveal').forEach(block => observer.observe(block));
}
