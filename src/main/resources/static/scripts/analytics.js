class AnalyticsDashboard {
    constructor() {
        this.shortCodeInput = document.getElementById('shortCodeInput');
        this.searchBtn = document.getElementById('searchBtn');
        this.loading = document.getElementById('loading');
        this.error = document.getElementById('error');
        this.errorMessage = document.getElementById('errorMessage');
        this.analyticsContent = document.getElementById('analyticsContent');
        this.charts = {};
        this.init();
    }

    init() {
        this.bindEvents();
        this.checkUrlParams();
    }

    bindEvents() {
        this.searchBtn.addEventListener('click', () => this.handleSearch());
        this.shortCodeInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') this.handleSearch();
        });
        this.shortCodeInput.addEventListener('input', () => this.hideMessages());
    }

    checkUrlParams() {
        const urlParams = new URLSearchParams(window.location.search);
        const shortCodeParam = urlParams.get('code');
        if (shortCodeParam) {
            this.shortCodeInput.value = shortCodeParam;
            this.loadAnalytics(shortCodeParam);
        }
    }

    hideMessages() {
        this.error.classList.remove('show');
        this.analyticsContent.classList.remove('show');
    }

    showError(message) {
        this.errorMessage.textContent = message;
        this.error.classList.add('show');
        console.error('Analytics Error:', message);
    }

    showLoading(show) {
        this.loading.classList.toggle('show', show);
        this.searchBtn.disabled = show;
        this.searchBtn.textContent = show ? 'Loading...' : 'Get Analytics';
    }

    destroyCharts() {
        Object.values(this.charts).forEach(chart => chart?.destroy());
        this.charts = {};
    }

    createCountryChart(clicksByCountry) {
        const ctx = document.getElementById('countryChart').getContext('2d');

        if (!clicksByCountry || clicksByCountry.length === 0) {
            this.showNoDataMessage(ctx, 'No geographic data available');
            return;
        }

        const validCountries = clicksByCountry
            .filter(([country, count]) => country !== null && count > 0)
            .slice(0, 8);

        const colors = [
            '#667eea', '#764ba2', '#f093fb', '#f5576c',
            '#4facfe', '#00f2fe', '#43e97b', '#38f9d7',
            '#ffecd2', '#fcb69f', '#a8edea', '#fed6e3'
        ];

        this.charts.country = new Chart(ctx, {
            type: 'doughnut',
            data: {
                labels: validCountries.map(([country]) => country),
                datasets: [{
                    data: validCountries.map(([, count]) => count),
                    backgroundColor: colors.slice(0, validCountries.length),
                    borderWidth: 0,
                    hoverOffset: 8
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        position: 'bottom',
                        labels: {
                            padding: 20,
                            font: { family: 'Inter', size: 12 },
                            generateLabels: function(chart) {
                                const data = chart.data;
                                const dataset = data.datasets[0];
                                return data.labels.map((label, i) => ({
                                    text: `${label} (${dataset.data[i]})`,
                                    fillStyle: dataset.backgroundColor[i]
                                }));
                            }
                        }
                    },
                    tooltip: {
                        backgroundColor: 'rgba(0,0,0,0.8)',
                        titleColor: 'white',
                        bodyColor: 'white',
                        borderColor: 'rgba(255,255,255,0.1)',
                        borderWidth: 1,
                        cornerRadius: 8,
                        callbacks: {
                            label: function(context) {
                                const total = context.dataset.data.reduce((a, b) => a + b, 0);
                                const percentage = ((context.parsed / total) * 100).toFixed(1);
                                return `${context.label}: ${context.parsed} (${percentage}%)`;
                            }
                        }
                    }
                },
                cutout: '60%'
            }
        });
    }

    createDeviceChart(clicksByDevice) {
        const ctx = document.getElementById('deviceChart').getContext('2d');

        if (!clicksByDevice || clicksByDevice.length === 0) {
            this.showNoDataMessage(ctx, 'No device data available');
            return;
        }

        this.charts.device = new Chart(ctx, {
            type: 'pie',
            data: {
                labels: clicksByDevice.map(([device]) => device),
                datasets: [{
                    data: clicksByDevice.map(([, count]) => count),
                    backgroundColor: ['#667eea', '#764ba2', '#f093fb', '#4facfe', '#43e97b'],
                    borderWidth: 0,
                    hoverOffset: 6
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        position: 'bottom',
                        labels: {
                            padding: 20,
                            font: { family: 'Inter', size: 12 },
                            generateLabels: function(chart) {
                                const data = chart.data;
                                const dataset = data.datasets[0];
                                return data.labels.map((label, i) => ({
                                    text: `${label} (${dataset.data[i]})`,
                                    fillStyle: dataset.backgroundColor[i]
                                }));
                            }
                        }
                    },
                    tooltip: {
                        backgroundColor: 'rgba(0,0,0,0.8)',
                        titleColor: 'white',
                        bodyColor: 'white',
                        borderColor: 'rgba(255,255,255,0.1)',
                        borderWidth: 1,
                        cornerRadius: 8,
                        callbacks: {
                            label: function(context) {
                                const total = context.dataset.data.reduce((a, b) => a + b, 0);
                                const percentage = ((context.parsed / total) * 100).toFixed(1);
                                return `${context.label}: ${context.parsed} (${percentage}%)`;
                            }
                        }
                    }
                }
            }
        });
    }

    createHourlyChart(clicksByHour) {
        const ctx = document.getElementById('hourlyChart').getContext('2d');

        if (!clicksByHour || clicksByHour.length === 0) {
            this.showNoDataMessage(ctx, 'No hourly data available');
            return;
        }

        const hourlyData = Array.from({length: 24}, (_, hour) => {
            const found = clicksByHour.find(([h]) => h === hour);
            return found ? found[1] : 0;
        });

        this.charts.hourly = new Chart(ctx, {
            type: 'line',
            data: {
                labels: Array.from({length: 24}, (_, i) => `${i}:00`),
                datasets: [{
                    label: 'Clicks',
                    data: hourlyData,
                    borderColor: '#667eea',
                    backgroundColor: 'rgba(102, 126, 234, 0.1)',
                    borderWidth: 3,
                    fill: true,
                    tension: 0.4,
                    pointBackgroundColor: '#667eea',
                    pointBorderColor: '#ffffff',
                    pointBorderWidth: 2,
                    pointRadius: 5,
                    pointHoverRadius: 8
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        backgroundColor: 'rgba(0,0,0,0.8)',
                        titleColor: 'white',
                        bodyColor: 'white',
                        borderColor: 'rgba(255,255,255,0.1)',
                        borderWidth: 1,
                        cornerRadius: 8
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        grid: { color: 'rgba(0,0,0,0.1)' },
                        ticks: { font: { family: 'Inter' } }
                    },
                    x: {
                        grid: { color: 'rgba(0,0,0,0.1)' },
                        ticks: { font: { family: 'Inter' } }
                    }
                }
            }
        });
    }

    createReferrerChart(topReferrers) {
        const ctx = document.getElementById('referrerChart').getContext('2d');

        if (!topReferrers || topReferrers.length === 0) {
            this.showNoDataMessage(ctx, 'No referrer data available');
            return;
        }

        const processedReferrers = topReferrers.slice(0, 6).map(([ref, count]) => {
            let label = ref;
            if (ref === 'null' || ref === '' || !ref) {
                label = 'Direct Traffic';
            } else if (ref.length > 25) {
                label = ref.substring(0, 25) + '...';
            }
            return [label, count];
        });

        this.charts.referrer = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: processedReferrers.map(([label]) => label),
                datasets: [{
                    label: 'Clicks',
                    data: processedReferrers.map(([, count]) => count),
                    backgroundColor: 'rgba(102, 126, 234, 0.8)',
                    borderColor: '#667eea',
                    borderWidth: 2,
                    borderRadius: 8,
                    borderSkipped: false,
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        backgroundColor: 'rgba(0,0,0,0.8)',
                        titleColor: 'white',
                        bodyColor: 'white',
                        borderColor: 'rgba(255,255,255,0.1)',
                        borderWidth: 1,
                        cornerRadius: 8
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        grid: { color: 'rgba(0,0,0,0.1)' },
                        ticks: { font: { family: 'Inter' } }
                    },
                    x: {
                        grid: { display: false },
                        ticks: {
                            font: { family: 'Inter' },
                            maxRotation: 45
                        }
                    }
                }
            }
        });
    }

    showNoDataMessage(ctx, message) {
        ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height);
        ctx.font = '16px Inter';
        ctx.fillStyle = '#94a3b8';
        ctx.textAlign = 'center';
        ctx.fillText(message, ctx.canvas.width / 2, ctx.canvas.height / 2);
    }

    renderRecentClicks(recentClicks) {
        const container = document.getElementById('recentClicksData');

        if (!recentClicks || recentClicks.length === 0) {
            container.innerHTML = '<div class="no-data">No recent clicks to display</div>';
            return;
        }

        container.innerHTML = recentClicks.map(click => `
            <div class="click-item">
                <div class="click-header">
                    <span class="click-time">${this.formatTimestamp(click.timestamp)}</span>
                    <span class="click-device">${click.deviceType || 'Unknown'}</span>
                </div>
                <div class="click-details">
                    <div class="click-detail"><strong>Browser:</strong> ${click.browser || 'Unknown'} ${click.browserVersion || ''}</div>
                    <div class="click-detail"><strong>OS:</strong> ${click.os || 'Unknown'} ${click.osVersion || ''}</div>
                    <div class="click-detail"><strong>Country:</strong> ${click.country || 'Unknown'}</div>
                    <div class="click-detail"><strong>City:</strong> ${click.city || 'Unknown'}</div>
                    <div class="click-detail"><strong>Referrer:</strong> ${this.formatReferrer(click.referer)}</div>
                    <div class="click-detail"><strong>IP:</strong> ${click.ipAddress || 'Unknown'}</div>
                </div>
            </div>
        `).join('');
    }

    formatReferrer(referer) {
        if (referer === 'null' || referer === '' || !referer) {
            return 'Direct Traffic';
        }
        return referer.length > 40 ? referer.substring(0, 40) + '...' : referer;
    }

    handleSearch() {
        const shortCode = this.shortCodeInput.value.trim();
        if (!shortCode) {
            this.showError('Please enter a short code');
            return;
        }
        if (!this.isValidShortCode(shortCode)) {
            this.showError('Please enter a valid short code (3-20 alphanumeric characters)');
            return;
        }
        this.loadAnalytics(shortCode);
    }

    async loadAnalytics(shortCode) {
        this.hideMessages();
        this.showLoading(true);

        try {
            console.log('Loading analytics for short code:', shortCode);

            const response = await fetch(`/api/analytics/${shortCode}`);

            if (!response.ok) {
                if (response.status === 404) {
                    throw new Error('Short code not found. Please check the code and try again.');
                } else if (response.status === 403) {
                    throw new Error('Access denied. You do not have permission to view this data.');
                } else {
                    throw new Error(`HTTP ${response.status}: Failed to load analytics`);
                }
            }

            const data = await response.json();
            console.log('Analytics data received:', data);

            this.renderAnalytics(data);
            this.analyticsContent.classList.add('show');

        } catch (err) {
            console.error('Error loading analytics:', err);
            this.showError(err.message || 'Failed to load analytics. Please check the short code and try again.');
        } finally {
            this.showLoading(false);
        }
    }

    renderAnalytics(data) {
        try {
            this.destroyCharts();

            this.updateElement('totalClicks', data.totalClicks?.toLocaleString() || '0');
            this.updateElement('uniqueCountries', data.clicksByCountry?.filter(([country]) => country !== null).length || '0');
            this.updateElement('uniqueDevices', data.clicksByDevice?.length || '0');

            const peakHour = data.clicksByHour?.reduce((max, current) =>
                current[1] > max[1] ? current : max, [0, 0]
            );
            this.updateElement('peakHour', peakHour && peakHour[1] > 0 ? `${peakHour}:00` : 'N/A');

            setTimeout(() => {
                this.createCountryChart(data.clicksByCountry);
                this.createDeviceChart(data.clicksByDevice);
                this.createHourlyChart(data.clicksByHour);
                this.createReferrerChart(data.topReferrers);
            }, 100);

            this.renderRecentClicks(data.recentClicks);

        } catch (err) {
            console.error('Error rendering analytics:', err);
            this.showError('Error displaying analytics data');
        }
    }

    updateElement(id, value) {
        try {
            const element = document.getElementById(id);
            if (element) {
                element.textContent = value;
            } else {
                console.warn(`Element not found: ${id}`);
            }
        } catch (err) {
            console.error(`Error updating element ${id}:`, err);
        }
    }

    formatTimestamp(timestamp) {
        try {
            const date = new Date(timestamp);
            return date.toLocaleString('en-US', {
                month: 'short',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit'
            });
        } catch (err) {
            console.error('Error formatting timestamp:', err);
            return 'Invalid Date';
        }
    }

    isValidShortCode(code) {
        return /^[a-zA-Z0-9-_]{3,20}$/.test(code);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    new AnalyticsDashboard();
});
