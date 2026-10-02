// 文档内容仅作为 render 的字符串参数传入，不拼接到脚本或 HTML 中。
window.renderNotepadDiagram = async function (source, dark) {
    try {
        mermaid.initialize({
            startOnLoad: false,
            securityLevel: 'strict',
            theme: dark ? 'dark' : 'default',
            fontFamily: 'sans-serif',
            htmlLabels: false,
            // SVG 标签仍按 <br/> 等显式换行分行，节点宽度由最长一行决定。
            // 无限折行宽度用于关闭自动拆行，不会给节点设置固定宽度。
            flowchart: { htmlLabels: false, wrappingWidth: Number.POSITIVE_INFINITY },
            maxTextSize: 50000,
            maxEdges: 500,
            suppressErrorRendering: true,
            secure: ['secure', 'securityLevel', 'startOnLoad', 'maxTextSize',
                'maxEdges', 'suppressErrorRendering', 'htmlLabels', 'flowchart']
        });
        const result = await mermaid.render('notepad-diagram', source);
        const host = document.getElementById('diagram');
        host.innerHTML = result.svg;
        const svg = host.querySelector('svg');
        const bounds = svg.viewBox.baseVal;
        if (!(bounds.width > 0 && bounds.height > 0)) {
            throw new Error('Diagram has no measurable bounds');
        }
        svg.removeAttribute('height');
        svg.removeAttribute('width');
        svg.style.cssText = 'display:block;width:100%;height:auto;max-width:none';
        window.diagramResult = {
            svg: svg.outerHTML,
            width: bounds.width,
            height: bounds.height
        };
        document.title = 'diagram-ready';
    } catch (error) {
        window.diagramError = String(error.message || error);
        document.title = 'diagram-failed';
    }
};
