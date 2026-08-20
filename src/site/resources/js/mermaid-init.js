(() => {
    "use strict";

    async function renderMermaid() {
        const codeBlocks = Array.from(document.querySelectorAll("pre > code.language-mermaid"));
        if (codeBlocks.length === 0) {
            return;
        }

        const module = await import("https://cdn.jsdelivr.net/npm/mermaid@11.15.0/dist/mermaid.esm.min.mjs");
        const mermaid = module.default;
        const diagrams = codeBlocks.map((codeBlock) => {
            const container = document.createElement("div");
            container.className = "mermaid";
            container.textContent = codeBlock.textContent;
            codeBlock.parentElement.replaceWith(container);
            return container;
        });

        mermaid.initialize({startOnLoad: false, securityLevel: "strict"});
        await mermaid.run({nodes: diagrams});
    }

    const start = () => renderMermaid().catch((error) => {
        console.error("Unable to render Mermaid diagrams", error);
    });

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", start, {once: true});
    } else {
        start();
    }
})();
