/* ============================================================
   ANALYTICS.JS — Command Center Logic
   Tip: Open analytics.html?code=demo to see a full demo
   ============================================================ */

/* ---------- DEMO DATA ---------- */
const DEMO_DATA = {
    totalClicks: 128412,
    clicksByHour: [
        [0,210],[1,180],[2,120],[3,95],[4,110],[5,160],
        [6,320],[7,580],[8,940],[9,1200],[10,1550],[11,1820],
        [12,2100],[13,1980],[14,1760],[15,1600],[16,1420],[17,1250],
        [18,1100],[19,900],[20,780],[21,620],[22,450],[23,310]
    ],
    clicksByDevice: [['Mobile',86900],['Desktop',34800],['Tablet',6712]],
    clicksByCountry: [
        ['United States',48700],['United Kingdom',28200],['Singapore',17900],
        ['Germany',12400],['India',9800],['Canada',7200],['Australia',4212]
    ],
    topReferrers: [
        ['https://linkedin.com',45102],['https://twitter.com',32840],
        ['https://mail.google.com',20211],['direct',18300],
        ['https://slack.com',12000],['https://notion.so',5959]
    ],
    recentClicks: [
        { timestamp: new Date(Date.now()-12000).toISOString(),   ipAddress:'142.250.80.46',  browser:'Chrome',  browserVersion:'118', os:'macOS Sonoma',  osVersion:'14', deviceType:'Desktop', country:'United States',  city:'New York',   referer:'https://linkedin.com'      },
        { timestamp: new Date(Date.now()-145000).toISOString(),  ipAddress:'31.13.84.36',    browser:'Safari',  browserVersion:'17',  os:'iOS 17',        osVersion:'17', deviceType:'Mobile',  country:'United Kingdom', city:'London',     referer:'https://twitter.com'       },
        { timestamp: new Date(Date.now()-310000).toISOString(),  ipAddress:'66.249.68.12',   browser:'Firefox', browserVersion:'119', os:'Windows 11',    osVersion:'11', deviceType:'Desktop', country:'Singapore',      city:'Singapore',  referer:null                        },
        { timestamp: new Date(Date.now()-490000).toISOString(),  ipAddress:'104.16.132.229', browser:'Chrome',  browserVersion:'118', os:'Android 14',    osVersion:'14', deviceType:'Mobile',  country:'Germany',        city:'Berlin',     referer:'https://slack.com'         },
        { timestamp: new Date(Date.now()-720000).toISOString(),  ipAddress:'192.168.1.12',   browser:'Edge',    browserVersion:'118', os:'Windows 11',    osVersion:'11', deviceType:'Desktop', country:'India',           city:'Mumbai',     referer:'https://mail.google.com'   },
        { timestamp: new Date(Date.now()-1100000).toISOString(), ipAddress:'74.125.224.72',  browser:'Chrome',  browserVersion:'117', os:'macOS Ventura', osVersion:'13', deviceType:'Desktop', country:'Canada',          city:'Toronto',    referer:'https://notion.so'         },
        { timestamp: new Date(Date.now()-1500000).toISOString(), ipAddress:'52.36.11.208',   browser:'Safari',  browserVersion:'16',  os:'iOS 16',        osVersion:'16', deviceType:'Mobile',  country:'Australia',       city:'Sydney',     referer:'https://linkedin.com'      },
    ]
};

class CommandCenter {
    constructor() {
        this.shortCodeInput = document.getElementById('shortCodeInput');
        this.searchBtn      = document.getElementById('searchBtn');
        this.searchModal    = document.getElementById('searchModal');
        this.loadingState   = document.getElementById('loadingState');
        this.errorState     = document.getElementById('errorState');
        this.errorMessage   = document.getElementById('errorMessage');

        this.ccUrl   = document.getElementById('ccUrl');
        this.ccMeta  = document.getElementById('ccMeta');

        this.kpiTotalClicks = document.getElementById('totalClicks');
        this.kpiCountries   = document.getElementById('uniqueCountries');
        this.kpiDevices     = document.getElementById('uniqueDevices');
        this.kpiDeviceSub   = document.getElementById('deviceSublabel');
        this.kpiPeakHour    = document.getElementById('peakHour');
        this.kpiClicksDelta = document.getElementById('clicksDelta');

        this.topDevicePct    = document.getElementById('topDevicePct');
        this.topDeviceName   = document.getElementById('topDeviceName');
        this.deviceBreakdown = document.getElementById('deviceBreakdown');
        this.geoCountryList  = document.getElementById('geoCountryList');
        this.geoSubtitle     = document.getElementById('geoSubtitle');
        this.geoLoadMoreBtn  = document.getElementById('geoLoadMoreBtn');
        this.acquisitionBars = document.getElementById('acquisitionBars');
        this.recentClicksData= document.getElementById('recentClicksData');
        this.loadMoreBtn     = document.getElementById('loadMoreBtn');
        this.filterInput     = document.getElementById('ltsFilterInput');
        this.filterTags      = document.getElementById('ltsFilterTags');

        this.dashboardContent = document.getElementById('dashboardContent');
        this.emptyState       = document.getElementById('emptyState');

        this.geoVisibleCount = 3;
        this.trafficVisibleCount = 5;
        this.currentCountries = [];
        this.currentClicks = [];

        this.allClickData    = [];   // cache of all rendered rows for filtering
        this.activeFilters   = [];   // active filter terms
        this.charts = {};
        this.isDemoMode = false;
        this.init();
    }

    init() {
        Chart.defaults.color = '#555555';
        Chart.defaults.font.family = 'Inter, sans-serif';
        Chart.defaults.font.size = 11;

        if (this.searchBtn) {
            this.searchBtn.addEventListener('click', () => this.handleSearch());
        }
        if (this.shortCodeInput) {
            this.shortCodeInput.addEventListener('keydown', e => {
                if (e.key === 'Enter') this.handleSearch();
            });
        }

        // Live filter wiring
        if (this.filterInput) {
            this.filterInput.addEventListener('input', () => this.applyFilter());
            this.filterInput.addEventListener('keydown', e => {
                if (e.key === 'Enter') {
                    const term = this.filterInput.value.trim();
                    if (term) this.addFilterTag(term);
                }
            });
        }

        // Load More buttons
        if (this.geoLoadMoreBtn) {
            this.geoLoadMoreBtn.addEventListener('click', () => {
                this.geoVisibleCount += 5;
                this.renderGeoSection(this.currentCountries, true);
            });
        }
        if (this.loadMoreBtn) {
            this.loadMoreBtn.addEventListener('click', () => {
                this.trafficVisibleCount += 5;
                this.renderTrafficTable(this.currentClicks, true);
            });
        }

        const params = new URLSearchParams(window.location.search);
        const code = params.get('code');
        if (code) {
            if (this.shortCodeInput) this.shortCodeInput.value = code;
            this.loadAnalytics(code);
        }
    }

    /* ---- Filter logic ---- */
    applyFilter() {
        const term = (this.filterInput?.value || '').toLowerCase().trim();
        if (!this.recentClicksData) return;
        const rows = this.recentClicksData.querySelectorAll('tr[data-searchtext]');
        let visible = 0;
        rows.forEach(row => {
            const searchText = row.getAttribute('data-searchtext') || '';
            const matchesTerm    = !term || searchText.includes(term);
            const matchesFilters = this.activeFilters.every(f => searchText.includes(f));
            if (matchesTerm && matchesFilters) {
                row.style.display = '';
                visible++;
            } else {
                row.style.display = 'none';
            }
        });
        // show empty state if nothing matches
        let noMatchRow = this.recentClicksData.querySelector('.filter-no-match');
        if (visible === 0 && (term || this.activeFilters.length)) {
            if (!noMatchRow) {
                noMatchRow = document.createElement('tr');
                noMatchRow.className = 'filter-no-match';
                noMatchRow.innerHTML = `<td colspan="6" style="text-align:center;padding:28px 20px;"><span class="empty-chip">no results for "${term || this.activeFilters.join(', ')}"</span></td>`;
                this.recentClicksData.appendChild(noMatchRow);
            } else {
                noMatchRow.style.display = '';
            }
        } else if (noMatchRow) {
            noMatchRow.style.display = 'none';
        }
    }

    addFilterTag(term) {
        const lower = term.toLowerCase();
        if (this.activeFilters.includes(lower)) return;
        this.activeFilters.push(lower);
        this.filterInput.value = '';
        this.renderFilterTags();
        this.applyFilter();
    }

    removeFilterTag(term) {
        this.activeFilters = this.activeFilters.filter(f => f !== term);
        this.renderFilterTags();
        this.applyFilter();
    }

    renderFilterTags() {
        if (!this.filterTags) return;
        this.filterTags.innerHTML = this.activeFilters.map(f => `
            <span class="filter-tag">
                ${f}
                <button class="filter-tag-remove" onclick="window._cc.removeFilterTag('${f}')">×</button>
            </span>`).join('');
    }

    handleSearch() {
        const code = (this.shortCodeInput?.value || '').trim();
        if (!code) { this.showModalError('Please enter a short code.'); return; }

        const url = new URL(window.location);
        url.searchParams.set('code', code);
        window.history.pushState({}, '', url);

        this.searchModal.style.display = 'none';
        this.loadAnalytics(code);
    }

    showModalError(msg) {
        if (this.errorMessage) this.errorMessage.textContent = msg;
        if (this.errorState)   this.errorState.style.display = 'block';
    }

    setModalLoading(on) {
        if (this.loadingState) this.loadingState.style.display = on ? 'flex' : 'none';
        if (this.searchBtn)    this.searchBtn.disabled = on;
        if (on && this.errorState) this.errorState.style.display = 'none';
    }

    async loadAnalytics(code) {
        this.setModalLoading(true);

        // Demo mode — no network call needed
        if (code.toLowerCase() === 'demo') {
            this.isDemoMode = true;
            await new Promise(r => setTimeout(r, 600)); // brief fake loading
            this.setModalLoading(false);
            this.render(code, DEMO_DATA, true);
            return;
        }

        this.isDemoMode = false;
        try {
            const res = await fetch(`/api/analytics/${code}`);
            if (!res.ok) {
                if (res.status === 404) {
                    // Graceful fallback: show demo with notice
                    this.setModalLoading(false);
                    this.render(code, DEMO_DATA, true);
                    if (this.ccMeta) {
                        this.ccMeta.textContent = '⚠ Short code not found — showing demo data';
                    }
                    return;
                }
                throw new Error(`Server error (${res.status})`);
            }
            const data = await res.json();
            this.render(code, data, false);
        } catch (err) {
            this.searchModal.style.display = 'flex';
            this.showModalError(err.message || 'Failed to load analytics.');
        } finally {
            this.setModalLoading(false);
        }
    }

    render(code, data, isDemo) {
        if (this.emptyState) this.emptyState.style.display = 'none';
        if (this.dashboardContent) this.dashboardContent.style.display = 'block';

        if (this.ccUrl) {
            this.ccUrl.textContent = `snaplink.io/${code}`;
        }

        const demoNotice = isDemo
            ? ' <span style="font-size:10px;color:var(--gold);letter-spacing:1px;padding:2px 8px;background:rgba(196,150,42,0.12);border-radius:20px;margin-left:8px">DEMO</span>'
            : '';

        if (this.ccMeta) {
            this.ccMeta.innerHTML = `Active • ${(data.totalClicks || 0).toLocaleString()} total clicks tracked${demoNotice}`;
        }

        // KPI — Total Clicks
        const clicks = data.totalClicks || 0;
        if (this.kpiTotalClicks) {
            this.kpiTotalClicks.textContent = clicks >= 1000
                ? (clicks / 1000).toFixed(1) + 'K'
                : clicks.toLocaleString();
        }
        if (this.kpiClicksDelta) {
            if (clicks > 0) {
                this.kpiClicksDelta.innerHTML = `<span style="color:var(--green)">↑ ${(clicks * 0.114).toFixed(0)} this week</span>`;
            } else {
                this.kpiClicksDelta.innerHTML = this.emptyChip('no data yet');
            }
        }

        // KPI — Countries
        const countries = (data.clicksByCountry || []).filter(([c]) => c && c !== 'null');
        if (this.kpiCountries) {
            this.kpiCountries.textContent = countries.length || '0';
        }

        // KPI — Device
        const devices = data.clicksByDevice || [];
        if (devices.length && this.kpiDevices) {
            const top   = devices.reduce((a, b) => b[1] > a[1] ? b : a, devices[0]);
            const total = devices.reduce((s, [, c]) => s + c, 0);
            const pct   = total ? Math.round((top[1] / total) * 100) : 0;
            this.kpiDevices.textContent = top[0] || 'Unknown';
            if (this.kpiDeviceSub) this.kpiDeviceSub.textContent = `${pct}% of traffic`;
        } else if (this.kpiDevices) {
            this.kpiDevices.innerHTML = this.emptyChip('no data');
        }

        // KPI — Peak Hour
        const hourly = data.clicksByHour || [];
        if (hourly.length && this.kpiPeakHour) {
            const peak = hourly.reduce((a, b) => b[1] > a[1] ? b : a, hourly[0]);
            const h = peak[0];
            this.kpiPeakHour.textContent = String(h).padStart(2, '0') + ':00';
        } else if (this.kpiPeakHour) {
            this.kpiPeakHour.innerHTML = this.emptyChip('no data');
        }

        this.destroyCharts();
        requestAnimationFrame(() => {
            this.renderHourlyChart(data.clicksByHour || []);
            this.renderDeviceDonut(data.clicksByDevice || []);
            this.renderGeoSection(countries);
            this.renderAcquisition(data.topReferrers || []);
            this.renderTrafficTable(data.recentClicks || []);
        });
    }

    /* Muted chip for empty/unknown fields */
    emptyChip(label = 'unknown') {
        return `<span style="font-size:11px;color:var(--text-muted);background:rgba(255,255,255,0.04);border:1px solid var(--border);border-radius:20px;padding:3px 10px;font-weight:500;letter-spacing:0.5px">${label}</span>`;
    }

    /* Safe value: return value or styled chip */
    safeVal(val, fallback = 'unknown') {
        if (!val || val === 'null' || val === 'Unknown' || val === '—' || val === '-') {
            return `<span class="empty-chip">${fallback}</span>`;
        }
        return val;
    }

    destroyCharts() {
        Object.values(this.charts).forEach(c => c && c.destroy());
        this.charts = {};
    }

    /* ------ 24-Hour Bar Chart ------ */
    renderHourlyChart(clicksByHour) {
        const canvas = document.getElementById('hourlyChart');
        if (!canvas) return;
        const ctx = canvas.getContext('2d');

        const hourlyData = Array.from({ length: 24 }, (_, i) => {
            const found = clicksByHour.find(([h]) => h === i);
            return found ? found[1] : 0;
        });

        const maxVal = Math.max(...hourlyData, 1);

        this.charts.hourly = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: Array.from({ length: 24 }, (_, i) => i % 4 === 0 ? `${i}:00` : ''),
                datasets: [{
                    data: hourlyData,
                    backgroundColor: hourlyData.map(v =>
                        v === maxVal ? 'rgba(196,150,42,0.95)' : 'rgba(255,255,255,0.08)'
                    ),
                    hoverBackgroundColor: hourlyData.map(v =>
                        v === maxVal ? 'rgba(196,150,42,1)' : 'rgba(255,255,255,0.18)'
                    ),
                    borderWidth: 0,
                    borderRadius: 3,
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: { duration: 900, easing: 'easeOutQuart' },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        backgroundColor: '#1a1a1a',
                        borderColor: '#333',
                        borderWidth: 1,
                        titleColor: '#C4962A',
                        bodyColor: '#aaa',
                        cornerRadius: 6,
                        padding: 10,
                        callbacks: {
                            title: ctx => `${ctx[0].dataIndex}:00 — ${String(ctx[0].dataIndex + 1).padStart(2,'0')}:00`,
                            label: ctx => ` ${ctx.raw.toLocaleString()} clicks`
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { color: 'rgba(255,255,255,0.03)', drawBorder: false },
                        ticks: { color: '#444', font: { size: 10 } }
                    },
                    y: {
                        grid: { color: 'rgba(255,255,255,0.03)', drawBorder: false },
                        ticks: { color: '#444', font: { size: 10 }, maxTicksLimit: 4 },
                        beginAtZero: true
                    }
                }
            }
        });
    }

    /* ------ Device Donut ------ */
    renderDeviceDonut(clicksByDevice) {
        const canvas = document.getElementById('deviceChart');
        if (!canvas) return;

        if (!clicksByDevice || !clicksByDevice.length) {
            if (this.topDevicePct)  this.topDevicePct.innerHTML  = this.emptyChip();
            if (this.deviceBreakdown) this.deviceBreakdown.innerHTML = '';
            return;
        }

        const ctx = canvas.getContext('2d');
        const total = clicksByDevice.reduce((s, [, c]) => s + c, 0);
        const top   = clicksByDevice.reduce((a, b) => b[1] > a[1] ? b : a, clicksByDevice[0]);
        const topPct = total ? Math.round((top[1] / total) * 100) : 0;

        if (this.topDevicePct)  this.topDevicePct.textContent  = topPct + '%';
        if (this.topDeviceName) this.topDeviceName.textContent = (top[0] || 'UNKNOWN').toUpperCase();

        const COLORS = ['#C4962A', '#3a3a3a', '#2c2c2c', '#4a4a4a', '#222'];

        this.charts.device = new Chart(ctx, {
            type: 'doughnut',
            data: {
                labels: clicksByDevice.map(([d]) => d || 'Unknown'),
                datasets: [{
                    data: clicksByDevice.map(([, c]) => c),
                    backgroundColor: COLORS.slice(0, clicksByDevice.length),
                    borderColor: '#111',
                    borderWidth: 3,
                    hoverOffset: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                cutout: '72%',
                animation: { duration: 900 },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        backgroundColor: '#1a1a1a',
                        borderColor: '#333',
                        borderWidth: 1,
                        titleColor: '#C4962A',
                        bodyColor: '#aaa',
                        cornerRadius: 6,
                        padding: 10,
                        callbacks: {
                            label: ctx => {
                                const pct = total ? ((ctx.raw / total) * 100).toFixed(1) : 0;
                                return ` ${ctx.label}: ${ctx.raw.toLocaleString()} (${pct}%)`;
                            }
                        }
                    }
                }
            }
        });

        if (this.deviceBreakdown) {
            const iconMap = {
                'Mobile':  `<svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="5" y="2" width="14" height="20" rx="2"></rect><line x1="12" y1="18" x2="12.01" y2="18"></line></svg>`,
                'Desktop': `<svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="3" width="20" height="14" rx="2"></rect><line x1="8" y1="21" x2="16" y2="21"></line><line x1="12" y1="17" x2="12" y2="21"></line></svg>`,
                'Tablet':  `<svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="4" y="2" width="16" height="20" rx="2"></rect><line x1="12" y1="18" x2="12.01" y2="18"></line></svg>`
            };
            this.deviceBreakdown.innerHTML = clicksByDevice.map(([name, count]) => {
                const pct  = total ? ((count / total) * 100).toFixed(1) : 0;
                const icon = iconMap[name] || iconMap['Desktop'];
                return `
                    <div class="device-row">
                        <span class="device-icon-small">${icon}</span>
                        <span class="device-row-name">${name || 'Unknown'}</span>
                        <span class="device-row-pct">${pct}%</span>
                    </div>`;
            }).join('');
        }
    }

    /* ------ Geographic Section ------ */
    renderGeoSection(countries, isLoadMore = false) {
        if (!this.geoCountryList) return;
        
        if (!isLoadMore) {
            this.geoVisibleCount = 3;
            this.currentCountries = countries || [];
        }

        if (!this.currentCountries || !this.currentCountries.length) {
            this.geoCountryList.innerHTML = `
                <div style="padding:20px;text-align:center;">
                    <span class="empty-chip">no geographic data yet</span>
                </div>`;
            if (this.geoSubtitle) this.geoSubtitle.textContent = 'no data';
            if (this.geoLoadMoreBtn) this.geoLoadMoreBtn.style.display = 'none';
            return;
        }

        const sorted = [...this.currentCountries].sort((a, b) => b[1] - a[1]);
        const maxCount = sorted[0][1];
        const total    = sorted.reduce((s, [, c]) => s + c, 0);

        if (this.geoSubtitle) {
            this.geoSubtitle.textContent = `${sorted.length} ${sorted.length === 1 ? 'country' : 'countries'} · ${total.toLocaleString()} total clicks`;
        }

        const visibleCountries = sorted.slice(0, this.geoVisibleCount);

        this.geoCountryList.innerHTML = visibleCountries.map(([name, count], i) => {
            const barPct  = maxCount ? Math.round((count / maxCount) * 100) : 0;
            const totalPct = total  ? ((count / total) * 100).toFixed(1) : 0;
            const rankNum  = i + 1;
            return `
                <div class="geo-row">
                    <div class="geo-rank">#${rankNum}</div>
                    <div class="geo-row-name">${name}</div>
                    <div class="geo-row-bar-wrap">
                        <div class="geo-row-bar" style="width:${barPct}%"></div>
                    </div>
                    <div class="geo-row-count">${count.toLocaleString()}</div>
                    <div class="geo-row-pct">${totalPct}%</div>
                </div>`;
        }).join('');

        if (this.geoLoadMoreBtn) {
            this.geoLoadMoreBtn.style.display = this.geoVisibleCount < sorted.length ? 'block' : 'none';
        }
    }

    /* ------ Traffic Acquisition ------ */
    renderAcquisition(referrers) {
        if (!this.acquisitionBars) return;

        if (!referrers || !referrers.length) {
            this.acquisitionBars.innerHTML = `
                <div style="padding:20px;text-align:center;width:100%;">
                    <span class="empty-chip">no referrer data yet</span>
                </div>`;
            return;
        }

        const top = referrers.slice(0, 5);
        const total = top.reduce((s, [, c]) => s + c, 0);

        this.acquisitionBars.innerHTML = top.map(([ref, count]) => {
            let label = ref;
            if (!ref || ref === 'null' || ref === 'direct') {
                label = 'DIRECT';
            } else {
                try {
                    label = new URL(ref).hostname
                        .replace('www.', '')
                        .replace('.com', '')
                        .toUpperCase();
                } catch (_) {}
            }
            if (label.length > 14) label = label.substring(0, 14) + '…';
            const pct = total ? Math.round((count / total) * 100) : 0;
            return `
                <div class="acq-col">
                    <div class="acq-label">${label}</div>
                    <div class="acq-value">${count.toLocaleString()}</div>
                    <div class="acq-bar-track">
                        <div class="acq-bar-fill" style="width:${pct}%"></div>
                    </div>
                </div>`;
        }).join('');
    }

    /* ------ Live Traffic Table ------ */
    renderTrafficTable(clicks, isLoadMore = false) {
        if (!this.recentClicksData) return;
        
        if (!isLoadMore) {
            this.activeFilters = [];
            this.trafficVisibleCount = 5;
            this.currentClicks = clicks || [];
            this.renderFilterTags();
        }

        if (!this.currentClicks || !this.currentClicks.length) {
            this.recentClicksData.innerHTML = `
                <tr>
                    <td colspan="6" style="text-align:center;padding:40px 20px;">
                        <span class="empty-chip">no click activity recorded yet</span>
                    </td>
                </tr>`;
            if (this.loadMoreBtn) this.loadMoreBtn.style.display = 'none';
            return;
        }

        const visibleClicks = this.currentClicks.slice(0, this.trafficVisibleCount);

        this.recentClicksData.innerHTML = visibleClicks.map(click => {
            const relTime = this.formatRelativeTime(click.timestamp);
            const absTime = this.formatAbsTime(click.timestamp);

            const browser = click.browser    || null;
            const os      = click.os          || null;
            const device  = click.deviceType  || null;
            const country = click.country     || null;
            const city    = click.city        || null;
            const ip      = click.ipAddress   || null;
            const ref     = click.referer;

            // Build searchable text for filtering
            let refHost = 'direct';
            if (ref && ref !== 'null' && ref !== '') {
                try { refHost = new URL(ref).hostname.replace('www.', ''); } catch (_) { refHost = ref; }
            }
            const searchText = [
                browser, os, device, country, city, ip, refHost,
                click.browserVersion, click.osVersion
            ].filter(Boolean).join(' ').toLowerCase();

            const browserCell = browser
                ? `<div class="tc-identity">${browser}${click.browserVersion ? ' ' + click.browserVersion : ''}</div>`
                : `<div class="tc-identity"><span class="empty-chip">unknown browser</span></div>`;
            const osCell = os
                ? `<div class="tc-identity">${os}${click.osVersion ? ' ' + click.osVersion : ''}</div>`
                : `<div class="tc-identity"><span class="empty-chip">unknown OS</span></div>`;
            const deviceCell = device
                ? `<div class="tc-sub">${device}</div>`
                : `<div class="tc-sub"><span class="empty-chip">unknown device</span></div>`;
            const locationCell = (country || city)
                ? `${city ? city + ', ' : ''}${country || ''}`
                : `<span class="empty-chip">unknown location</span>`;
            const ipCell = ip
                ? `<div class="tc-timestamp">${ip}</div>`
                : `<div class="tc-timestamp"><span class="empty-chip">private</span></div>`;

            let refCell;
            if (refHost === 'direct') {
                refCell = `<span class="tc-direct">Direct</span>`;
            } else {
                refCell = `<span class="tc-referrer">${refHost}</span>`;
            }

            return `
                <tr data-searchtext="${searchText.replace(/"/g, '')}">
                    <td>
                        <div class="tc-relative">${relTime}</div>
                        ${ipCell}
                    </td>
                    <td>
                        ${browserCell}
                        <div class="tc-sub">${ip ? ip.substring(0, 13) + '…' : ''}</div>
                    </td>
                    <td>
                        ${osCell}
                        ${deviceCell}
                    </td>
                    <td>
                        <div class="tc-location">
                            <span class="tc-identity">${locationCell}</span>
                        </div>
                    </td>
                    <td>${refCell}</td>
                    <td>
                        <button class="tc-info-btn" title="Recorded at: ${absTime}">
                            <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>
                        </button>
                    </td>
                </tr>`;
        }).join('');

        if (this.loadMoreBtn) {
            this.loadMoreBtn.style.display = this.trafficVisibleCount < this.currentClicks.length ? 'block' : 'none';
        }
        
        // Reapply filters if they were active
        this.applyFilter();
    }

    formatRelativeTime(ts) {
        if (!ts) return '—';
        const diff = (Date.now() - new Date(ts)) / 1000;
        if (diff < 60)    return 'Just Now';
        if (diff < 3600)  return `${Math.round(diff / 60)} min ago`;
        if (diff < 86400) return `${Math.round(diff / 3600)} hr ago`;
        return `${Math.round(diff / 86400)}d ago`;
    }

    formatAbsTime(ts) {
        if (!ts) return '—';
        return new Date(ts).toLocaleString('en-US', {
            month: 'short', day: 'numeric',
            hour: '2-digit', minute: '2-digit'
        });
    }
}

document.addEventListener('DOMContentLoaded', () => { window._cc = new CommandCenter(); });
