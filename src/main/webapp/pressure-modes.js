// Shared pressure mode loading for index.html and comparison.html
// Data source: GET /api/pressure/modes -> Map<String, PressureStatusResponse>

(function () {
    'use strict';

    var contextRoot = window.location.pathname.split('/')[1];
    var API_BASE = (contextRoot ? '/' + contextRoot : '') + '/api';
    var cachedModes = null;
    var containers = [];

    function groupModes(modes) {
        var groups = {
            'Control': [],
            'Memory Scenarios': [],
            'CPU Workloads': []
        };

        Object.keys(modes).forEach(function (name) {
            var info = modes[name];
            if (info.workloadType !== 'NONE') {
                groups['CPU Workloads'].push(name);
            } else if (info.scenarioType !== 'NONE') {
                groups['Memory Scenarios'].push(name);
            } else {
                groups['Control'].push(name);
            }
        });

        return groups;
    }

    function formatName(name) {
        return name.replace(/_/g, ' ');
    }

    function createEl(tag, attrs) {
        var el = document.createElement(tag);
        if (attrs) {
            Object.keys(attrs).forEach(function (key) {
                if (key === 'className') el.className = attrs[key];
                else if (key === 'textContent') el.textContent = attrs[key];
                else if (key === 'style') el.style.cssText = attrs[key];
                else el.setAttribute(key, attrs[key]);
            });
        }
        return el;
    }

    function renderButtons(container, modes, grouped, onSelect) {
        while (container.firstChild) container.removeChild(container.firstChild);

        var groupOrder = ['Memory Scenarios', 'CPU Workloads', 'Control'];
        groupOrder.forEach(function (groupLabel) {
            var modeNames = grouped[groupLabel];
            if (!modeNames || modeNames.length === 0) return;

            var groupDiv = createEl('div', { style: 'display:flex;gap:12px;flex-wrap:wrap;margin-bottom:8px;' });

            if (groupLabel !== 'Control') {
                var label = createEl('span', {
                    style: 'font-size:0.75em;color:#94a3b8;font-weight:600;text-transform:uppercase;align-self:center;min-width:110px;',
                    textContent: groupLabel
                });
                groupDiv.appendChild(label);
            }

            modeNames.forEach(function (name) {
                var info = modes[name];
                var btn = createEl('button', {
                    className: 'scenario-btn',
                    'data-mode': name
                });
                btn.id = 'btn-' + name;

                btn.appendChild(document.createTextNode(formatName(name)));

                var hint = '';
                if (info.allocationRateMBPerSec > 0) {
                    hint = info.allocationRateMBPerSec + 'MB/s';
                    if (info.liveSetSizeMB > 0) {
                        hint += ', ' + info.liveSetSizeMB + 'MB Live';
                    }
                }
                if (hint) {
                    var hintSpan = createEl('span', { className: 'hint', textContent: hint });
                    btn.appendChild(hintSpan);
                }

                btn.addEventListener('click', function (e) {
                    e.stopPropagation();
                    onSelect(name);
                });

                groupDiv.appendChild(btn);
            });

            container.appendChild(groupDiv);
        });
    }

    function populateSelectOptions(select, modes, grouped) {
        while (select.firstChild) select.removeChild(select.firstChild);

        select.appendChild(createEl('option', { value: '', textContent: '-- select mode --' }));

        var groupOrder = ['Memory Scenarios', 'CPU Workloads', 'Control'];
        groupOrder.forEach(function (groupLabel) {
            var modeNames = grouped[groupLabel];
            if (!modeNames || modeNames.length === 0) return;

            var optgroup = createEl('optgroup', { label: groupLabel });

            modeNames.forEach(function (name) {
                var opt = createEl('option', { value: name, textContent: formatName(name) });
                optgroup.appendChild(opt);
            });

            select.appendChild(optgroup);
        });
    }

    function renderSelect(container, modes, grouped, onSelect, stopPropagation) {
        var existingSelect = container.querySelector('select');
        if (existingSelect) {
            populateSelectOptions(existingSelect, modes, grouped);
            return;
        }

        while (container.firstChild) container.removeChild(container.firstChild);

        var select = createEl('select', {
            className: 'scenario-select'
        });
        select.id = container.dataset.selectId || 'pressure-mode-select';

        populateSelectOptions(select, modes, grouped);

        select.addEventListener('change', function () {
            if (this.value) {
                onSelect(this.value);
                this.value = '';
            }
        });

        if (stopPropagation) {
            select.addEventListener('click', function (e) {
                e.stopPropagation();
            });
        }

        container.appendChild(select);
    }

    function fetchModes() {
        return fetch(API_BASE + '/pressure/modes')
            .then(function (resp) {
                if (!resp.ok) throw new Error('HTTP ' + resp.status);
                return resp.json();
            });
    }

    window.PressureModes = {
        load: function (containerEl, options) {
            options = options || {};
            containers.push({ container: containerEl, options: options });

            if (!containerEl.querySelector('select')) {
                var placeholder = createEl('span', {
                    style: 'color:#64748b;font-size:0.85em;',
                    textContent: 'Loading modes...'
                });
                while (containerEl.firstChild) containerEl.removeChild(containerEl.firstChild);
                containerEl.appendChild(placeholder);
            }

            return fetchModes().then(function (modes) {
                cachedModes = modes;
                var grouped = groupModes(modes);
                var layout = options.layout || 'buttons';

                if (layout === 'select') {
                    renderSelect(containerEl, modes, grouped, options.onSelect || function () {}, options.stopPropagation);
                } else {
                    renderButtons(containerEl, modes, grouped, options.onSelect || function () {});
                }
            }).catch(function (err) {
                console.error('Failed to load pressure modes:', err);
                var existingSelect = containerEl.querySelector('select');
                if (!existingSelect) {
                    while (containerEl.firstChild) containerEl.removeChild(containerEl.firstChild);
                    containerEl.appendChild(createEl('span', {
                        style: 'color:#ef4444;font-size:0.85em;',
                        textContent: 'Failed to load modes'
                    }));
                }
            });
        },

        refresh: function () {
            if (!cachedModes) return;
            return fetchModes().then(function (modes) {
                cachedModes = modes;
                var grouped = groupModes(modes);
                containers.forEach(function (record) {
                    var layout = record.options.layout || 'buttons';
                    if (layout === 'select') {
                        renderSelect(record.container, modes, grouped, record.options.onSelect || function () {}, record.options.stopPropagation);
                    } else {
                        renderButtons(record.container, modes, grouped, record.options.onSelect || function () {});
                    }
                });
            });
        },

        getModes: function () {
            return cachedModes;
        },

        setActiveMode: function (modeName) {
            document.querySelectorAll('.scenario-btn').forEach(function (btn) {
                btn.classList.toggle('active', btn.getAttribute('data-mode') === modeName);
            });
        }
    };
})();
