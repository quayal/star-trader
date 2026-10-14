// Mockup-only interactivity — no backend, just enough to demo UX flows.

function initTabs(root) {
	root.querySelectorAll('[data-tabs]').forEach(group => {
		const tabs = group.querySelectorAll('.tab');
		tabs.forEach(tab => {
			tab.addEventListener('click', () => {
				tabs.forEach(t => t.classList.remove('active'));
				tab.classList.add('active');
				const target = tab.getAttribute('data-target');
				group.closest('[data-tab-scope]').querySelectorAll('[data-tab-panel]').forEach(panel => {
					panel.style.display = panel.getAttribute('data-tab-panel') === target ? '' : 'none';
				});
			});
		});
	});
}

function initSegmented(root) {
	root.querySelectorAll('.segmented').forEach(seg => {
		seg.querySelectorAll('button').forEach(btn => {
			btn.addEventListener('click', () => {
				seg.querySelectorAll('button').forEach(b => b.classList.remove('active'));
				btn.classList.add('active');
			});
		});
	});
}

document.addEventListener('DOMContentLoaded', () => {
	initTabs(document);
	initSegmented(document);
});
