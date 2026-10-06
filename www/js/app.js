document.addEventListener("DOMContentLoaded", () => {
    const liveTimeBox = document.getElementById("live-time");
    const refreshTimeBtn = document.getElementById("refresh-time-btn");
    const healthBox = document.getElementById("health-box");
    const checkHealthBtn = document.getElementById("check-health-btn");
    const echoForm = document.getElementById("echo-form");
    const echoInput = document.getElementById("echo-input");
    const echoOutput = document.getElementById("echo-output");
    const uploadForm = document.getElementById("upload-form");
    const uploadFilename = document.getElementById("upload-filename");
    const uploadContent = document.getElementById("upload-content");
    const uploadStatus = document.getElementById("upload-status");

    // Fetch and render live server time
    const fetchTime = async () => {
        try {
            liveTimeBox.textContent = "Querying /api/time...";
            const res = await fetch("/api/time");
            if (!res.ok) throw new Error("HTTP " + res.status);
            const data = await res.json();
            liveTimeBox.textContent = `UTC: ${data.utc}\nLocal: ${data.local}`;
        } catch (err) {
            liveTimeBox.textContent = "Error: " + err.message;
        }
    };

    // Check server health
    const checkHealth = async () => {
        try {
            healthBox.textContent = "Checking /health...";
            const res = await fetch("/health");
            if (!res.ok) throw new Error("HTTP " + res.status);
            const data = await res.json();
            healthBox.textContent = JSON.stringify(data);
        } catch (err) {
            healthBox.textContent = "Error: " + err.message;
        }
    };

    // Handle echo form submission
    echoForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        try {
            echoOutput.textContent = "Sending POST request...";
            const payload = echoInput.value;
            const res = await fetch("/api/echo", {
                method: "POST",
                headers: { "Content-Type": "text/plain" },
                body: payload
            });
            const data = await res.json();
            echoOutput.textContent = JSON.stringify(data, null, 2);
        } catch (err) {
            echoOutput.textContent = "Error: " + err.message;
        }
    });

    // Handle upload form submission
    uploadForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        try {
            uploadStatus.textContent = "Uploading...";
            uploadStatus.style.color = "var(--text-secondary)";
            const filename = uploadFilename.value.trim();
            const content = uploadContent.value;

            const res = await fetch("/upload", {
                method: "POST",
                headers: {
                    "X-Filename": filename,
                    "Content-Type": "text/plain"
                },
                body: content
            });

            if (res.status === 201) {
                const loc = res.headers.get("Location") || `/uploads/${filename}`;
                uploadStatus.textContent = `Successfully uploaded! Location: ${loc}`;
                uploadStatus.style.color = "var(--success)";
            } else {
                const text = await res.text();
                uploadStatus.textContent = `Upload failed (${res.status}): ${text}`;
                uploadStatus.style.color = "var(--danger)";
            }
        } catch (err) {
            uploadStatus.textContent = "Error: " + err.message;
            uploadStatus.style.color = "var(--danger)";
        }
    });

    refreshTimeBtn.addEventListener("click", fetchTime);
    checkHealthBtn.addEventListener("click", checkHealth);

    // Initial load calls
    fetchTime();
    checkHealth();
});
