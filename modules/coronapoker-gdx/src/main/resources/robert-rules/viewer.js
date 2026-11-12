document.addEventListener("webviewerloaded", () => {
    const language = new URLSearchParams(location.search).get("gameLocale") === "es"
        ? "es-ES" : "en-US";
    document.documentElement.lang = language;
    const options = window.PDFViewerApplicationOptions;
    options.set("localeProperties", { lang: language });
    // Start the new raster as soon as the zoom changes.
    options.set("defaultZoomDelay", -1);
    // Chromium reports the embedded window as its screen. Keep the canvas
    // budget independent of that window's size and retain a fixed upper limit.
    options.set("capCanvasAreaFactor", -1);
    options.set("maxCanvasPixels", 24 * 1024 * 1024);
    installSelectionMenu(language === "es-ES");
});

function installSelectionMenu(spanish) {
    const viewer = document.getElementById("viewerContainer");
    const menu = document.createElement("div");
    const copy = document.createElement("button");
    const copyLabel = spanish ? "Copiar" : "Copy";
    menu.className = "game-selection-menu";
    menu.setAttribute("role", "menu");
    menu.setAttribute("aria-label", spanish ? "Texto seleccionado" : "Selected text");
    menu.hidden = true;
    copy.type = "button";
    copy.setAttribute("role", "menuitem");
    copy.textContent = copyLabel;
    menu.append(copy);
    document.body.append(menu);

    let selectedText = "";
    let previousFocus;

    function selection() {
        const current = window.getSelection();
        return current && !current.isCollapsed
            && viewer.contains(current.anchorNode) && viewer.contains(current.focusNode)
            ? current.toString().replaceAll("\0", "").trim() : "";
    }

    function close(restoreFocus = false) {
        if (menu.hidden) return;
        menu.hidden = true;
        selectedText = "";
        window.cefQuery?.({ request: "menu:close" });
        if (restoreFocus) previousFocus?.focus({ preventScroll: true });
    }

    function copyText(text, onSuccess, onFailure = () => {}) {
        if (window.cefQuery) {
            window.cefQuery({ request: "copy:" + text, onSuccess, onFailure });
        } else {
            navigator.clipboard.writeText(text).then(onSuccess, onFailure);
        }
    }

    viewer.addEventListener("contextmenu", event => {
        event.preventDefault();
        const text = selection();
        if (!text) {
            close();
            return;
        }
        if (menu.hidden) previousFocus = document.activeElement;
        selectedText = text;
        copy.disabled = false;
        copy.textContent = copyLabel;
        menu.hidden = false;
        menu.style.left = Math.max(6, Math.min(event.clientX, innerWidth - menu.offsetWidth - 6)) + "px";
        menu.style.top = Math.max(6, Math.min(event.clientY, innerHeight - menu.offsetHeight - 6)) + "px";
        copy.focus({ preventScroll: true });
        window.cefQuery?.({ request: "menu:open" });
    });

    copy.addEventListener("pointerdown", event => event.preventDefault());
    copy.addEventListener("click", () => {
        if (!selectedText) return;
        copy.disabled = true;
        copyText(selectedText, () => close(true), () => {
            copy.disabled = false;
            copy.textContent = spanish ? "Reintentar copia" : "Retry copy";
            copy.focus({ preventScroll: true });
        });
    });
    window.addEventListener("rules-copy-selection", () => {
        const field = document.activeElement;
        let text = selectedText || selection();
        if (field?.matches("input, textarea")) {
            text = field.selectionStart === null ? "" : field.value.slice(field.selectionStart, field.selectionEnd);
        }
        if (text) copyText(text, () => close(true));
    });
    document.addEventListener("copy", event => {
        const text = selectedText || selection();
        if (!text) return;
        event.preventDefault();
        event.stopImmediatePropagation();
        if (!window.cefQuery && event.clipboardData) {
            event.clipboardData.setData("text/plain", text);
            close(true);
            return;
        }
        copyText(text, () => close(true));
    }, true);
    document.addEventListener("pointerdown", event => {
        if (!menu.contains(event.target) && event.button !== 2) close();
    }, true);
    menu.addEventListener("keydown", event => {
        if (event.key === "Escape") {
            event.preventDefault();
            close(true);
        }
    });
    viewer.addEventListener("scroll", () => close());
    window.addEventListener("resize", () => close());
    window.addEventListener("rules-dismiss-selection-menu", () => close(true));
}
