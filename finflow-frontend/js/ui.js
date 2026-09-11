(function () {
    function refreshIcons() {
        if (!window.lucide || typeof window.lucide.createIcons !== 'function') {
            return;
        }

        window.lucide.createIcons({
            attrs: {
                'aria-hidden': 'true',
                'stroke-width': 1.8
            }
        });
    }

    function initials(name) {
        return (name || 'F')
            .trim()
            .split(/\s+/)
            .map(part => part.charAt(0))
            .join('')
            .slice(0, 2)
            .toUpperCase();
    }

    document.addEventListener('DOMContentLoaded', () => {
        const username = sessionStorage.getItem('username') || '';

        document.querySelectorAll('[data-user-initial]').forEach(element => {
            element.textContent = initials(username);
        });

        document.querySelectorAll('[data-nav]').forEach(link => {
            link.classList.toggle('is-active', link.dataset.nav === document.body.dataset.page);
        });

        enableCardTilt();

        refreshIcons();
    });

    function enableCardTilt() {
        if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
            return;
        }

        document.querySelectorAll('[data-tilt-card]').forEach(card => {
            card.addEventListener('pointermove', event => {
                if (event.pointerType === 'touch') return;
                const bounds = card.getBoundingClientRect();
                const x = (event.clientX - bounds.left) / bounds.width - 0.5;
                const y = (event.clientY - bounds.top) / bounds.height - 0.5;
                card.style.transform = `rotateX(${6 - y * 7}deg) rotateY(${-7 + x * 9}deg)`;
            });

            card.addEventListener('pointerleave', () => {
                card.style.removeProperty('transform');
            });
        });
    }

    window.FinFlowUI = { refreshIcons };
}());
