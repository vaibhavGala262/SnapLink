class URLShortener {
    constructor() {
        this.form = document.getElementById('shortenForm');
        this.urlInput = document.getElementById('urlInput');
        this.aliasInput = document.getElementById('aliasInput');
        this.customAliasToggle = document.getElementById('customAliasToggle');
        this.aliasInputWrapper = document.getElementById('aliasInputWrapper');
        this.shortenBtn = document.getElementById('shortenBtn');
        this.loading = document.getElementById('loading');
        this.resultSection = document.getElementById('resultSection');
        this.shortenedUrl = document.getElementById('shortenedUrl');
        this.copyBtn = document.getElementById('copyBtn');
        this.error = document.getElementById('error');
        this.errorMessage = document.getElementById('errorMessage');
        this.analyticsSection = document.getElementById('analyticsSection');
        this.viewAnalyticsBtn = document.getElementById('viewAnalyticsBtn');
        this.quickAnalytics = document.getElementById('quickAnalytics');
        this.refreshStats = document.getElementById('refreshStats');

        this.currentShortCode = '';
        this.init();
    }

    init() {
        this.bindEvents();
        this.hideMessages();
    }

    bindEvents() {
        // Form submission
        this.form.addEventListener('submit', (e) => this.handleSubmit(e));

        // Copy functionality
        this.copyBtn.addEventListener('click', () => this.handleCopy());

        // Custom alias toggle
        this.customAliasToggle.addEventListener('change', () => this.toggleCustomAlias());

        // Analytics buttons
        this.viewAnalyticsBtn.addEventListener('click', () => this.openAnalytics());
        this.refreshStats.addEventListener('click', () => this.refreshAnalytics());

        // Clear messages when user starts typing
        this.urlInput.addEventListener('input', () => this.hideMessages());
        this.aliasInput.addEventListener('input', () => this.hideMessages());

        // Validate alias input
        this.aliasInput.addEventListener('input', (e) => this.validateAlias(e));
    }

    toggleCustomAlias() {
        if (this.customAliasToggle.checked) {
            this.aliasInputWrapper.style.display = 'block';
            setTimeout(() => {
                this.aliasInputWrapper.classList.add('show');
                this.aliasInput.focus();
            }, 10);
        } else {
            this.aliasInputWrapper.classList.remove('show');
            setTimeout(() => {
                this.aliasInputWrapper.style.display = 'none';
                this.aliasInput.value = '';
            }, 300);
        }
    }

    validateAlias(e) {
        const value = e.target.value;
        const regex = /^[a-zA-Z0-9-_]*$/;

        if (value && !regex.test(value)) {
            e.target.setCustomValidity('Only letters, numbers, hyphens, and underscores are allowed');
            this.showError('Custom alias can only contain letters, numbers, hyphens, and underscores');
        } else if (value && value.length < 3) {
            e.target.setCustomValidity('Custom alias must be at least 3 characters long');
            this.showError('Custom alias must be at least 3 characters long');
        } else if (value && value.length > 20) {
            e.target.setCustomValidity('Custom alias must be less than 20 characters');
            this.showError('Custom alias must be less than 20 characters');
        } else {
            e.target.setCustomValidity('');
            this.hideMessages();
        }
    }

    hideMessages() {
        this.error.classList.remove('show');
        this.resultSection.classList.remove('show');
        this.analyticsSection.classList.remove('show');
    }

    showError(message) {
        this.errorMessage.textContent = message;
        this.error.classList.add('show');
        console.error('URL Shortener Error:', message);
    }

    showLoading(show) {
        this.loading.classList.toggle('show', show);
        this.shortenBtn.disabled = show;
        this.shortenBtn.textContent = show ? 'Shortening...' : 'Shorten URL';
    }

    async loadQuickAnalytics(shortCode) {
        try {
            console.log('Loading analytics for:', shortCode);
            const response = await fetch(`/api/analytics/${shortCode}`);

            if (response.ok) {
                const data = await response.json();
                console.log('Analytics data received:', data);

                document.getElementById('quickClicks').textContent = data.totalClicks || 0;
                document.getElementById('quickCountries').textContent = data.clicksByCountry?.length || 0;
                document.getElementById('quickDevices').textContent = data.clicksByDevice?.length || 0;
                document.getElementById('quickReferrers').textContent = data.topReferrers?.length || 0;

                this.quickAnalytics.style.display = 'block';
            } else {
                console.warn('Analytics not available:', response.status);
                this.quickAnalytics.style.display = 'none';
            }
        } catch (err) {
            console.warn('Analytics error:', err.message);
            this.quickAnalytics.style.display = 'none';
        }
    }

    async handleSubmit(e) {
        e.preventDefault();
        this.hideMessages();

        const url = this.urlInput.value.trim();
        const customAlias = this.customAliasToggle.checked ? this.aliasInput.value.trim() : '';

        // Validate inputs
        if (!url) {
            this.showError('Please enter a valid URL');
            return;
        }

        if (!this.isValidUrl(url)) {
            this.showError('Please enter a valid URL with http:// or https://');
            return;
        }

        if (customAlias && !this.isValidAlias(customAlias)) {
            this.showError('Custom alias must be 3-20 characters long and contain only letters, numbers, hyphens, and underscores');
            return;
        }

        this.showLoading(true);

        try {
            const formData = new FormData();
            formData.append('url', url);
            if (customAlias) {
                formData.append('alias', customAlias);
            }

            const response = await fetch('/api/shorten', {
                method: 'POST',
                body: formData
            });

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`HTTP ${response.status}: ${errorText || 'Unknown error'}`);
            }

            const shortUrl = await response.text();
            this.currentShortCode = shortUrl.split('/').pop();

            console.log('URL shortened successfully:', {
                original: url,
                shortened: shortUrl,
                shortCode: this.currentShortCode
            });

            this.shortenedUrl.value = shortUrl;
            this.resultSection.classList.add('show');
            this.analyticsSection.classList.add('show');

            // Load initial analytics after a short delay
            setTimeout(() => this.loadQuickAnalytics(this.currentShortCode), 1000);

            // Reset copy button
            this.copyBtn.textContent = 'Copy';
            this.copyBtn.classList.remove('copied');

        } catch (err) {
            console.error('Error shortening URL:', err);

            if (err.message.includes('409')) {
                this.showError('Custom alias is already taken. Please choose a different one.');
            } else if (err.message.includes('400')) {
                this.showError('Invalid request. Please check your input and try again.');
            } else {
                this.showError('Failed to shorten URL. Please try again.');
            }
        } finally {
            this.showLoading(false);
        }
    }

    async handleCopy() {
        try {
            await navigator.clipboard.writeText(this.shortenedUrl.value);
            this.copyBtn.textContent = 'Copied!';
            this.copyBtn.classList.add('copied');

            console.log('URL copied to clipboard:', this.shortenedUrl.value);

            setTimeout(() => {
                this.copyBtn.textContent = 'Copy';
                this.copyBtn.classList.remove('copied');
            }, 2000);
        } catch (err) {
            console.error('Failed to copy:', err);

            // Fallback for older browsers
            try {
                this.shortenedUrl.select();
                this.shortenedUrl.setSelectionRange(0, 99999);
                document.execCommand('copy');

                this.copyBtn.textContent = 'Copied!';
                this.copyBtn.classList.add('copied');

                setTimeout(() => {
                    this.copyBtn.textContent = 'Copy';
                    this.copyBtn.classList.remove('copied');
                }, 2000);
            } catch (fallbackErr) {
                this.showError('Failed to copy URL. Please copy manually.');
            }
        }
    }

    openAnalytics() {
        if (this.currentShortCode) {
            const analyticsUrl = `analytics.html?code=${this.currentShortCode}`;
            window.open(analyticsUrl, '_blank');
            console.log('Opening analytics for:', this.currentShortCode);
        }
    }

    refreshAnalytics() {
        if (this.currentShortCode) {
            console.log('Refreshing analytics for:', this.currentShortCode);
            this.loadQuickAnalytics(this.currentShortCode);
        }
    }

    isValidUrl(string) {
        try {
            const url = new URL(string);
            return url.protocol === 'http:' || url.protocol === 'https:';
        } catch (_) {
            return false;
        }
    }

    isValidAlias(alias) {
        const regex = /^[a-zA-Z0-9-_]{3,20}$/;
        return regex.test(alias);
    }
}

// Initialize the application when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    console.log('Initializing URL Shortener application...');
    new URLShortener();
});
