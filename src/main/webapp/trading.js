// Trading Desk JavaScript

// Determine context path from current location
const CONTEXT_PATH = window.location.pathname.substring(0, window.location.pathname.indexOf('/', 1)) || '';
const API_BASE = CONTEXT_PATH + '/api';

// State
let selectedSymbol = 'AAPL';
let selectedSide = 'BUY';
let selectedOrderType = 'MARKET';
let executionsWs = null;
let executions = [];
let positions = {};
let orderBook = { bids: [], asks: [] };
let indicators = {};

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    initSymbolSelector();
    initOrderTicket();
    initPanelTabs();
    initDropdown();
    connectWebSocket();
    loadInitialData();
});

// Dropdown Navigation
function toggleDropdown() {
    const dropdown = document.getElementById('nav-dropdown');
    dropdown.classList.toggle('open');
}

function initDropdown() {
    document.addEventListener('click', (e) => {
        const dropdown = document.getElementById('nav-dropdown');
        if (dropdown && !dropdown.contains(e.target)) {
            dropdown.classList.remove('open');
        }
    });
}

// Symbol Selector
function initSymbolSelector() {
    document.querySelectorAll('.symbol-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.symbol-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            selectedSymbol = btn.dataset.symbol;
            document.getElementById('selected-symbol').textContent = selectedSymbol;
            loadOrderBook();
            loadPositions();
            loadIndicators();
            loadRiskMetrics();
        });
    });
}

// Order Ticket
function initOrderTicket() {
    // Order type tabs
    document.querySelectorAll('.order-tab').forEach(tab => {
        tab.addEventListener('click', () => {
            document.querySelectorAll('.order-tab').forEach(t => t.classList.remove('active'));
            tab.classList.add('active');
            selectedOrderType = tab.dataset.type;
            updateOrderForm();
        });
    });

    // Side buttons
    document.querySelectorAll('.side-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.side-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            selectedSide = btn.dataset.side;
            updateSubmitButton();
        });
    });

    // Submit order
    document.getElementById('submit-order').addEventListener('click', submitOrder);
}

function updateOrderForm() {
    const priceGroup = document.getElementById('price-group');
    const stopPriceGroup = document.getElementById('stop-price-group');

    priceGroup.style.display = selectedOrderType === 'LIMIT' ? 'block' : 'none';
    stopPriceGroup.style.display = selectedOrderType === 'STOP' ? 'block' : 'none';
}

function updateSubmitButton() {
    const btn = document.getElementById('submit-order');
    btn.className = 'submit-btn ' + selectedSide.toLowerCase();
    btn.textContent = 'Submit ' + (selectedSide === 'BUY' ? 'Buy' : 'Sell') + ' Order';
}

async function submitOrder() {
    const qty = parseInt(document.getElementById('order-qty').value);
    const price = parseFloat(document.getElementById('order-price').value) || 0;
    const stopPrice = parseFloat(document.getElementById('stop-price').value) || 0;
    const tif = document.getElementById('order-tif').value;

    const orderRequest = {
        symbol: selectedSymbol,
        side: selectedSide,
        type: selectedOrderType,
        quantity: qty,
        timeInForce: tif
    };

    if (selectedOrderType === 'LIMIT') {
        orderRequest.price = price;
    }
    if (selectedOrderType === 'STOP') {
        orderRequest.stopPrice = stopPrice;
    }

    try {
        const response = await fetch(API_BASE + '/matching/orders', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(orderRequest)
        });

        const result = await response.json();

        if (result.status === 'ACCEPTED') {
            showNotification('Order ' + result.orderId + ' accepted', 'success');
            document.getElementById('open-orders').textContent =
                (parseInt(document.getElementById('open-orders').textContent) + 1).toString();
        } else {
            showNotification('Order rejected: ' + result.reason, 'error');
        }
    } catch (e) {
        showNotification('Failed to submit order: ' + e.message, 'error');
    }
}

// Panel Tabs
function initPanelTabs() {
    document.querySelectorAll('.panel-tab').forEach(tab => {
        tab.addEventListener('click', () => {
            document.querySelectorAll('.panel-tab').forEach(t => t.classList.remove('active'));
            tab.classList.add('active');
            const panel = tab.dataset.panel;
            document.getElementById('executions-panel').style.display = panel === 'executions' ? 'block' : 'none';
            document.getElementById('risk-panel').style.display = panel === 'risk' ? 'block' : 'none';
        });
    });
}

// WebSocket Connection
function connectWebSocket() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = protocol + '//' + window.location.host + CONTEXT_PATH + '/executions';

    executionsWs = new WebSocket(wsUrl);

    executionsWs.onopen = () => {
        document.getElementById('ws-indicator').classList.add('connected');
        document.getElementById('ws-status').textContent = 'Connected';
    };

    executionsWs.onclose = () => {
        document.getElementById('ws-indicator').classList.remove('connected');
        document.getElementById('ws-status').textContent = 'Disconnected';
        setTimeout(connectWebSocket, 3000);
    };

    executionsWs.onmessage = (event) => {
        const data = JSON.parse(event.data);
        handleExecution(data);
    };

    executionsWs.onerror = () => {
        document.getElementById('ws-status').textContent = 'Error';
    };
}

function handleExecution(execution) {
    executions.unshift(execution);
    if (executions.length > 100) executions.pop();
    renderExecutions();
    updatePositionFromExecution(execution);
}

function updatePositionFromExecution(execution) {
    const sym = execution.symbol || selectedSymbol;
    if (!positions[sym]) {
        positions[sym] = { quantity: 0, avgPrice: 0 };
    }

    const pos = positions[sym];
    const signedQty = execution.side === 'BUY' ? execution.quantity : -execution.quantity;

    if (pos.quantity + signedQty === 0) {
        delete positions[sym];
    } else {
        const newQty = pos.quantity + signedQty;
        pos.avgPrice = (pos.avgPrice * pos.quantity + execution.price * signedQty) / newQty;
        pos.quantity = newQty;
    }

    renderPositions();
}

// Load Initial Data
async function loadInitialData() {
    await Promise.all([
        loadOrderBook(),
        loadPositions(),
        loadIndicators(),
        loadRiskMetrics()
    ]);
}

// Order Book
async function loadOrderBook() {
    try {
        const response = await fetch(API_BASE + '/matching/order-book/' + selectedSymbol);
        const snapshot = await response.json();

        if (snapshot) {
            orderBook = {
                bids: snapshot.bids || [],
                asks: snapshot.asks || []
            };
            renderOrderBook();
        }
    } catch (e) {
        renderEmptyOrderBook();
    }
}

function renderOrderBook() {
    const asksContainer = document.getElementById('asks');
    const bidsContainer = document.getElementById('bids');

    if (orderBook.asks.length === 0 && orderBook.bids.length === 0) {
        renderEmptyOrderBook();
        return;
    }

    // Clear containers
    asksContainer.textContent = '';
    bidsContainer.textContent = '';

    // Render asks (reversed to show highest ask at top)
    const sortedAsks = [...orderBook.asks].reverse();
    let askTotal = 0;
    for (const level of sortedAsks) {
        askTotal += level.quantity || 0;
        const row = createBookLevel('ask', level.priceDisplay || level.price, level.quantity, askTotal);
        asksContainer.appendChild(row);
    }

    // Render bids
    let bidTotal = 0;
    for (const level of orderBook.bids) {
        bidTotal += level.quantity || 0;
        const row = createBookLevel('bid', level.priceDisplay || level.price, level.quantity, bidTotal);
        bidsContainer.appendChild(row);
    }

    // Update spread
    if (orderBook.asks.length > 0 && orderBook.bids.length > 0) {
        const bestAsk = parseFloat(orderBook.asks[0].priceDisplay || orderBook.asks[0].price);
        const bestBid = parseFloat(orderBook.bids[0].priceDisplay || orderBook.bids[0].price);
        const spread = (bestAsk - bestBid).toFixed(4);
        document.getElementById('spread').textContent = spread;
        document.getElementById('last-price').textContent = formatPrice(bestBid);
    }
}

function createBookLevel(type, price, qty, total) {
    const row = document.createElement('div');
    row.className = 'book-level ' + type;

    const priceSpan = document.createElement('span');
    priceSpan.className = 'price';
    priceSpan.textContent = formatPrice(price);

    const qtySpan = document.createElement('span');
    qtySpan.textContent = String(qty);

    const totalSpan = document.createElement('span');
    totalSpan.textContent = String(total);

    row.appendChild(priceSpan);
    row.appendChild(qtySpan);
    row.appendChild(totalSpan);
    return row;
}

function renderEmptyOrderBook() {
    const asksContainer = document.getElementById('asks');
    const bidsContainer = document.getElementById('bids');

    asksContainer.textContent = '';
    bidsContainer.textContent = '';

    const emptyAsk = document.createElement('div');
    emptyAsk.className = 'book-level';
    emptyAsk.textContent = 'No asks';
    asksContainer.appendChild(emptyAsk);

    const emptyBid = document.createElement('div');
    emptyBid.className = 'book-level';
    emptyBid.textContent = 'No bids';
    bidsContainer.appendChild(emptyBid);

    document.getElementById('spread').textContent = '-';
}

// Positions
async function loadPositions() {
    try {
        const response = await fetch(API_BASE + '/matching/positions');
        const data = await response.json();

        if (data && typeof data === 'object') {
            // Backend returns Map<String, Position>, convert to array
            const positionsArray = Object.values(data);
            positionsArray.forEach(function(p) {
                if (p && p.symbol) {
                    positions[p.symbol] = {
                        quantity: p.netQuantity || 0,
                        avgPrice: p.averageEntryPrice || 0,
                        marketValue: p.marketValue || 0,
                        unrealizedPnl: p.unrealizedPnlTicks || 0
                    };
                }
            });
            renderPositions();
        }
    } catch (e) {
        renderEmptyPositions();
    }
}

function renderPositions() {
    const container = document.getElementById('positions-list');
    container.textContent = '';

    const symbols = Object.keys(positions);

    if (symbols.length === 0) {
        const emptyRow = document.createElement('div');
        emptyRow.className = 'position-row';

        const symSpan = document.createElement('span');
        symSpan.className = 'position-symbol';
        symSpan.textContent = 'No positions';
        emptyRow.appendChild(symSpan);
        container.appendChild(emptyRow);
        return;
    }

    for (const sym of symbols) {
        const pos = positions[sym];
        const row = document.createElement('div');
        row.className = 'position-row';

        const symSpan = document.createElement('span');
        symSpan.className = 'position-symbol';
        symSpan.textContent = sym;

        const qtySpan = document.createElement('span');
        qtySpan.className = 'position-qty ' + (pos.quantity > 0 ? 'long' : 'short');
        qtySpan.textContent = String(pos.quantity);

        const pnlSpan = document.createElement('span');
        const pnl = pos.unrealizedPnl || 0;
        pnlSpan.className = 'position-pnl ' + (pnl >= 0 ? 'positive' : 'negative');
        pnlSpan.textContent = (pnl >= 0 ? '+' : '') + formatCurrency(pnl);

        row.appendChild(symSpan);
        row.appendChild(qtySpan);
        row.appendChild(pnlSpan);
        container.appendChild(row);
    }
}

function renderEmptyPositions() {
    const container = document.getElementById('positions-list');
    container.textContent = '';

    const emptyRow = document.createElement('div');
    emptyRow.className = 'position-row';

    const symSpan = document.createElement('span');
    symSpan.className = 'position-symbol';
    symSpan.textContent = 'No positions';
    emptyRow.appendChild(symSpan);
    container.appendChild(emptyRow);
}

// Technical Indicators
async function loadIndicators() {
    try {
        const response = await fetch(API_BASE + '/analysis/' + selectedSymbol);
        const data = await response.json();

        if (data) {
            indicators = data.indicators || {};
            renderIndicators();
        }
    } catch (e) {
        renderEmptyIndicators();
    }
}

function renderIndicators() {
    document.getElementById('sma-20').textContent = formatPrice(indicators['SMA(20)']);
    document.getElementById('ema-12').textContent = formatPrice(indicators['EMA(12)']);
    document.getElementById('rsi-14').textContent = indicators['RSI(14)'] ? indicators['RSI(14)'].toFixed(2) : '-';
    document.getElementById('macd').textContent = indicators['MACD(12,26)'] ? indicators['MACD(12,26)'].toFixed(4) : '-';
}

function renderEmptyIndicators() {
    document.getElementById('sma-20').textContent = '-';
    document.getElementById('ema-12').textContent = '-';
    document.getElementById('rsi-14').textContent = '-';
    document.getElementById('macd').textContent = '-';
}

// Risk Metrics
async function loadRiskMetrics() {
    try {
        const response = await fetch(API_BASE + '/risk/metrics');
        const data = await response.json();

        if (data) {
            renderRiskMetrics(data);
        }
    } catch (e) {
        renderEmptyRiskMetrics();
    }
}

function renderRiskMetrics(data) {
    const varEl = document.getElementById('var-95');
    varEl.textContent = formatCurrency(data.valueAtRisk95);
    varEl.className = 'value ' + (data.valueAtRisk95 > 10000 ? 'warning' : 'success');

    document.getElementById('total-exposure').textContent = formatCurrency(data.totalExposure);
    document.getElementById('net-delta').textContent = data.netDelta || '0';

    // Handle maxDrawdown - ensure it's a valid number
    var drawdownValue = data.maxDrawdown;
    if (drawdownValue === null || drawdownValue === undefined || isNaN(drawdownValue)) {
        drawdownValue = 0;
    }
    document.getElementById('max-drawdown').textContent = (drawdownValue * 100).toFixed(2) + '%';

    if (data.stressTests) {
        document.getElementById('stress-flash').textContent = formatCurrency(data.stressTests.flashCrash);
        document.getElementById('stress-vol').textContent = formatCurrency(data.stressTests.volatilitySpike);
    }
}

function renderEmptyRiskMetrics() {
    document.getElementById('var-95').textContent = '$0';
    document.getElementById('total-exposure').textContent = '$0';
    document.getElementById('net-delta').textContent = '0';
    document.getElementById('max-drawdown').textContent = '0%';
    document.getElementById('stress-flash').textContent = '-$0';
    document.getElementById('stress-vol').textContent = '-$0';
}

// Executions
function renderExecutions() {
    const container = document.getElementById('executions-list');
    container.textContent = '';

    if (executions.length === 0) {
        const emptyRow = createExecutionRow('-', '-', 'No executions yet', '-', '-');
        container.appendChild(emptyRow);
        return;
    }

    for (const exec of executions.slice(0, 20)) {
        const time = new Date(exec.timestamp).toLocaleTimeString();
        const row = createExecutionRow(
            time,
            exec.side,
            exec.symbol || selectedSymbol,
            String(exec.quantity),
            formatPrice(exec.price)
        );
        container.appendChild(row);
    }
}

function createExecutionRow(time, side, symbol, qty, price) {
    const row = document.createElement('div');
    row.className = 'execution-row';

    const timeSpan = document.createElement('span');
    timeSpan.className = 'exec-time';
    timeSpan.textContent = time;

    const sideSpan = document.createElement('span');
    sideSpan.className = 'exec-side ' + (side.toLowerCase ? side.toLowerCase() : '');
    sideSpan.textContent = side;

    const symSpan = document.createElement('span');
    symSpan.className = 'exec-symbol';
    symSpan.textContent = symbol;

    const qtySpan = document.createElement('span');
    qtySpan.className = 'exec-qty';
    qtySpan.textContent = qty;

    const priceSpan = document.createElement('span');
    priceSpan.className = 'exec-price';
    priceSpan.textContent = price;

    row.appendChild(timeSpan);
    row.appendChild(sideSpan);
    row.appendChild(symSpan);
    row.appendChild(qtySpan);
    row.appendChild(priceSpan);
    return row;
}

// Notification
function showNotification(message, type) {
    const notification = document.createElement('div');
    notification.style.cssText = 'position:fixed;top:20px;right:20px;padding:12px 20px;border-radius:6px;font-size:0.9em;z-index:1000;animation:slideIn 0.3s ease;background:' +
        (type === 'success' ? 'var(--success)' : 'var(--danger)') + ';color:white;';
    notification.textContent = message;
    document.body.appendChild(notification);

    setTimeout(function() {
        notification.style.animation = 'slideOut 0.3s ease';
        setTimeout(function() { notification.remove(); }, 300);
    }, 3000);
}

// Formatting utilities
function formatPrice(price) {
    if (price === null || price === undefined) return '-';
    return typeof price === 'number' ? price.toFixed(2) : String(price);
}

function formatCurrency(value) {
    if (value === null || value === undefined) return '$0';
    const absVal = Math.abs(value);
    const prefix = value < 0 ? '-$' : '$';
    if (absVal >= 1e6) return prefix + (absVal / 1e6).toFixed(2) + 'M';
    if (absVal >= 1e3) return prefix + (absVal / 1e3).toFixed(2) + 'K';
    return prefix + absVal.toFixed(2);
}

// Add CSS animations
(function() {
    const style = document.createElement('style');
    style.textContent = '@keyframes slideIn{from{transform:translateX(100%);opacity:0}to{transform:translateX(0);opacity:1}}@keyframes slideOut{from{transform:translateX(0);opacity:1}to{transform:translateX(100%);opacity:0}}';
    document.head.appendChild(style);
})();
