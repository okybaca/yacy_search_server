/* Shared JSON/Pack import monitor. No document contents are interpreted as HTML. */
(function () {
    "use strict";
    var monitor = document.getElementById("import-monitor");
    var folders = document.getElementById("pack-folders");
    if (!monitor && !folders) return;
    var timer;
    var failures = 0;
    var polls = 0;
    var pending = false;
    var folderJobId;
    var stop = document.getElementById("import-stop");
    var stopButton = stop ? stop.querySelector("button") : null;
    var stale = document.getElementById("import-stale");

    function value(name, content) {
        monitor.querySelector('[data-value="' + name + '"]').textContent = content;
    }

    function duration(seconds) {
        var hours = Math.floor(seconds / 3600);
        var minutes = Math.floor(seconds / 60) % 60;
        return hours + ":" + ("0" + minutes).slice(-2) + ":" + ("0" + Math.floor(seconds % 60)).slice(-2);
    }

    function render(job) {
        if (!monitor) return;
        document.getElementById("import-loading").hidden = true;
        document.getElementById("import-empty").hidden = !!job;
        document.getElementById("import-details").hidden = !job;
        var selection = document.getElementById("import-selection");
        if (selection) selection.disabled = !!job && job.active;
        if (!job) return;
        var state = document.querySelector('[data-state="' + job.state + '"]');
        document.getElementById("import-state").textContent = state ? state.textContent : job.state;
        value("source", job.source);
        var percent = job.totalBytes > 0 ? Math.min(100, job.bytesRead * 100 / job.totalBytes) : 0;
        document.getElementById("import-progress").value = percent;
        value("fileProgress", percent.toFixed(1) + "% — " + (job.bytesRead / 1073741824).toFixed(2)
            + " / " + (job.totalBytes / 1073741824).toFixed(2) + " GiB");
        ["read", "submitted", "skipped", "errors", "ignoredFields", "lastProgressSeconds"].forEach(function (name) {
            value(name, job[name].toLocaleString());
        });
        value("speed", job.documentsPerSecond.toLocaleString(undefined, {maximumFractionDigits: 1}));
        value("elapsed", duration(job.elapsedSeconds));
        value("remaining", job.remainingSeconds < 0 ? document.getElementById("import-estimating").textContent : duration(job.remainingSeconds));
        value("queue", job.queueSize + " / " + job.queueCapacity);
        value("workers", job.activeWorkers + " / " + job.workers);
        document.getElementById("import-stalled").hidden = !job.stalled;
        document.getElementById("import-errors").hidden = job.recentErrors.length === 0;
        var errors = document.getElementById("import-error-list");
        errors.textContent = "";
        job.recentErrors.forEach(function (error) {
            var row = document.createElement("tr");
            [error.line || "—", error.document, error.message, error.occurrences].forEach(function (text) {
                var cell = document.createElement("td");
                cell.textContent = text;
                cell.style.overflowWrap = "anywhere";
                row.appendChild(cell);
            });
            errors.appendChild(row);
        });
        document.getElementById("import-job").value = job.id;
        stop.hidden = !job.active;
        stopButton.disabled = job.state === "STOPPING";
    }

    function linkPackActivity() {
        // Older page backends may not yet provide row snapshots. Keep the detailed importer page accessible.
        document.querySelectorAll("[data-pack-activity]").forEach(function (link) {
            var file = link.closest("[data-pack-file]").getAttribute("data-pack-file");
            // Supported pack filenames have an extension; an old backend's empty-list placeholder does not.
            if (file.indexOf(".") < 0) {
                link.closest("[data-pack-file]").textContent = "";
                return;
            }
            if (/\.(jsonl|jsonlist|flatjson)(\.gz)?$/.test(file)) link.href = "IndexImportJsonList_p.html";
            else if (/\.warc(\.gz)?$/.test(file)) link.href = "IndexImportWarc_p.html";
            else if (/\.zim$/.test(file)) link.href = "IndexImportZim_p.html";
        });
    }

    function renderPackRows(job) {
        document.querySelectorAll("[data-pack-progress-row]").forEach(function (row) {
            var file = row.closest("[data-pack-file]").getAttribute("data-pack-file");
            // Only the page backend associates a job with a pack, using its full source path.
            var matches = job && job.source === file && row.getAttribute("data-pack-job") === job.id;
            var details = row.querySelector("[data-pack-progress]");
            row.querySelector("[data-pack-stale]").hidden = true;
            details.hidden = !matches;
            if (!matches) {
                row.setAttribute("data-pack-job", "");
                row.querySelector("[data-pack-state]").textContent = document.getElementById("pack-waiting-label").textContent;
                return;
            }
            row.setAttribute("data-pack-job", job.id);
            var state = document.querySelector('[data-state="' + job.state + '"]');
            row.querySelector("[data-pack-state]").textContent = state ? state.textContent : job.state;
            var percent = job.totalBytes > 0 ? Math.min(100, job.bytesRead * 100 / job.totalBytes) : 0;
            row.querySelector("[data-pack-bar]").value = percent;
            row.querySelector("[data-pack-percent]").textContent = percent.toLocaleString(undefined, {maximumFractionDigits: 1});
            row.querySelector("[data-pack-submitted]").textContent = job.submitted.toLocaleString();
            row.querySelector("[data-pack-speed]").textContent = job.documentsPerSecond.toLocaleString(undefined, {maximumFractionDigits: 1});
        });
    }

    async function poll() {
        if (pending || document.hidden) return;
        pending = true;
        var controller = new AbortController();
        var timeout = setTimeout(function () { controller.abort(); }, 10000);
        try {
            var response = await fetch("api/importstatus_p.json", {cache: "no-store", credentials: "same-origin", signal: controller.signal});
            if (!response.ok) throw new Error("Status HTTP " + response.status);
            var status = await response.json();
            if (!Object.prototype.hasOwnProperty.call(status, "job")) throw new Error("Missing import status");
            render(status.job);
            renderPackRows(status.job);
            if (stale) stale.hidden = true;
            failures = 0;
            // Refresh folder contents without repeating action parameters.
            var jobId = status.job ? status.job.id : "";
            var finishedPack = status.job && !status.job.active
                && Array.from(document.querySelectorAll("[data-pack-job]")).some(function (row) {
                    return row.getAttribute("data-pack-job") === jobId;
                });
            if (folders && (polls++ % 5 === 0 || finishedPack || folderJobId !== jobId)) {
                var page = await fetch("IndexPackManager_p.html", {cache: "no-store", credentials: "same-origin", signal: controller.signal});
                if (!page.ok) throw new Error("Folders HTTP " + page.status);
                var content = new DOMParser().parseFromString(await page.text(), "text/html");
                var updated = content.getElementById("pack-folders");
                if (!updated) throw new Error("Missing pack folders");
                folders.replaceChildren.apply(folders, Array.from(updated.childNodes));
                folderJobId = jobId;
                linkPackActivity();
                renderPackRows(status.job);
            }
        } catch (error) {
            if (monitor) document.getElementById("import-loading").hidden = true;
            failures++;
            if (stale) stale.hidden = false;
            document.querySelectorAll("[data-pack-stale]").forEach(function (message) { message.hidden = false; });
        } finally {
            clearTimeout(timeout);
            pending = false;
            if (!document.hidden) timer = setTimeout(poll, Math.min(15000, 2000 * Math.pow(2, failures)));
        }
    }

    if (stop) stop.addEventListener("submit", async function (event) {
        event.preventDefault();
        stopButton.disabled = true;
        var controller = new AbortController();
        var timeout = setTimeout(function () { controller.abort(); }, 10000);
        try {
            var body = new URLSearchParams(new FormData(stop));
            body.set("abort", "1");
            var response = await fetch(stop.action, {method: "POST", body: body, credentials: "same-origin", signal: controller.signal});
            if (!response.ok) throw new Error("Stop HTTP " + response.status);
        } catch (error) {
            stale.hidden = false;
            stopButton.disabled = false;
        } finally {
            clearTimeout(timeout);
        }
        clearTimeout(timer);
        poll();
    });

    document.addEventListener("visibilitychange", function () {
        clearTimeout(timer);
        if (!document.hidden) poll();
    });
    linkPackActivity();
    poll();
}());
