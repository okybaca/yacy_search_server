/* Keep synchronous pack downloads visible without navigating away from the listing. */
(function () {
    "use strict";
    var root = document.getElementById("pack-downloader");
    if (!root || !window.fetch || !window.FormData || !window.URLSearchParams) return;
    var pending = false;

    // A completed legacy GET request must not be replayed by a browser refresh.
    var listingURL = new URL(window.location.href);
    if (listingURL.searchParams.has("dlsource")) {
        ["dlsource", "dlrepoid", "dlfile"].forEach(function (name) { listingURL.searchParams.delete(name); });
        window.history.replaceState(null, "", listingURL.pathname + listingURL.search + listingURL.hash);
    }

    root.addEventListener("submit", async function (event) {
        var form = event.target;
        if (!form.matches(".pack-download-form")) return;
        event.preventDefault();
        if (pending) return;
        pending = true;
        var body = new URLSearchParams(new FormData(form));
        var running = document.getElementById("pack-download-running");
        document.getElementById("pack-downloading-file").textContent = body.get("dlfile");
        running.hidden = false;
        document.getElementById("pack-download-uncertain").hidden = true;
        var previous = root.querySelectorAll("#pack-download-complete, #pack-download-error");
        previous.forEach(function (message) { message.hidden = true; });
        root.querySelectorAll(".pack-download-form button").forEach(function (button) { button.disabled = true; });
        form.querySelector("button").textContent = document.getElementById("pack-downloading-label").textContent;
        document.getElementById("available-packs").setAttribute("aria-busy", "true");
        running.focus();
        try {
            // This response is HTML; the archive stays on the server. Large transfers have no total-duration timeout here.
            var response = await fetch(form.action, {method: "POST", body: body, credentials: "same-origin", cache: "no-store"});
            if (!response.ok) throw new Error("Download HTTP " + response.status);
            var page = new DOMParser().parseFromString(await response.text(), "text/html");
            var updated = page.getElementById("pack-downloader");
            if (!updated || !updated.querySelector("#pack-download-complete, #pack-download-error")) {
                throw new Error("Missing download result");
            }
            root.replaceChildren.apply(root, Array.from(updated.childNodes));
            pending = false;
            root.querySelector("#pack-download-complete, #pack-download-error").focus();
        } catch (error) {
            running.hidden = true;
            document.getElementById("available-packs").setAttribute("aria-busy", "false");
            var uncertain = document.getElementById("pack-download-uncertain");
            uncertain.hidden = false;
            uncertain.focus();
            // The server may still be transferring. Keep download controls disabled until the user checks.
        }
    });
}());
