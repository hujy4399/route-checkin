(() => {
  'use strict';

  const root = document.querySelector('#view-root');
  const hero = document.querySelector('#hero');
  const authDialog = document.querySelector('#auth-dialog');
  const detailDialog = document.querySelector('#detail-dialog');
  const state = { view: 'routes', credentials: null, me: null, routes: [], products: [], points: [], keywords: [], selectedPoints: [], selectedKeywords: [], page: 0, query: '', keyword: '', authMode: 'login', map: null, mapMarker: null };
  let toastTimer;

  const h = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  const num = value => Number(value || 0).toLocaleString('zh-CN');
  const date = value => value ? String(value).replace('T', ' ').slice(0, 16) : '未设置';
  const status = value => ({ PENDING: '待审核', ACTIVE: '正常', REJECTED: '已拒绝', PUBLISHED: '已发布', CLOSED: '已关闭', OFF_SALE: '已下架', REDEEMED: '待领取', FULFILLED: '已领取', CANCELLED: '已取消' })[value] || value || '—';
  const heading = (kicker, title, description = '', count = '') => `<div class="section-heading"><div><span class="section-kicker">${h(kicker)}</span><h2>${h(title)}</h2>${description ? `<p>${h(description)}</p>` : ''}</div>${count !== '' ? `<span class="count-label">${h(count)}</span>` : ''}</div>`;
  const empty = (title, detail = '') => `<div class="empty"><span class="empty-icon">⌁</span><strong>${h(title)}</strong><p>${h(detail)}</p></div>`;
  const button = (label, action, id, cls = 'mini-button') => `<button class="${cls}" type="button" data-action="${action}" data-id="${Number(id)}">${h(label)}</button>`;

  function toast(message, error = false) {
    const el = document.querySelector('#toast');
    el.textContent = message;
    el.classList.toggle('error', error);
    el.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove('show'), 4200);
  }

  function basicHeader(username, password) {
    const bytes = new TextEncoder().encode(`${username}:${password}`);
    let binary = '';
    for (const byte of bytes) binary += String.fromCharCode(byte);
    return `Basic ${btoa(binary)}`;
  }

  async function api(path, options = {}) {
    const headers = { Accept: 'application/json' };
    if (options.body !== undefined) headers['Content-Type'] = 'application/json';
    if (state.credentials && options.auth !== false) headers.Authorization = basicHeader(...state.credentials);
    const response = await fetch(`/api${path}`, { method: options.method || 'GET', headers, body: options.body === undefined ? undefined : JSON.stringify(options.body) });
    if (!response.ok) {
      let info = {};
      try { info = await response.json(); } catch (_) { /* empty response */ }
      const message = info.detail || info.message || info.error || (response.status === 401 ? '请先登录，或检查账号密码和审核状态' : response.status === 403 ? '当前账号没有操作权限' : `请求失败（${response.status}）`);
      throw new Error(message);
    }
    if (response.status === 204 || response.headers.get('content-length') === '0') return null;
    const raw = await response.text();
    return raw ? JSON.parse(raw) : null;
  }

  function showError(error) { toast(error.message || '操作失败，请稍后重试', true); }
  function needLogin() { if (state.me) return true; authDialog.showModal(); toast('登录后即可继续操作'); return false; }
  async function refreshMe() {
    if (!state.credentials) return;
    try { state.me = await api('/auth/me'); }
    catch (error) { state.credentials = null; state.me = null; throw error; }
    syncAccount();
  }
  function syncAccount() {
    document.querySelector('#account-button').textContent = state.me ? `${state.me.nickname || state.me.username} · 退出` : '登录 / 注册';
    const balance = document.querySelector('#balance-pill');
    balance.classList.toggle('hidden', !state.me);
    balance.querySelector('b').textContent = num(state.me?.pointsBalance);
    document.querySelectorAll('.admin-only').forEach(el => el.classList.toggle('hidden', !state.me?.roles?.includes('ADMIN')));
  }

  async function navigate(view) {
    if (view === 'admin' && !state.me?.roles?.includes('ADMIN')) return;
    if (state.map) { state.map.remove(); state.map = null; state.mapMarker = null; }
    state.view = view;
    document.querySelectorAll('[data-view]').forEach(el => el.classList.toggle('active', el.dataset.view === view));
    hero.classList.toggle('hidden', view !== 'routes');
    root.innerHTML = `<div class="empty">正在加载…</div>`;
    window.scrollTo({ top: 0, behavior: 'smooth' });
    try {
      if (view === 'routes') await renderRoutes();
      else if (view === 'map') await renderMap();
      else if (view === 'publish') await renderPublish();
      else if (view === 'shop') await renderShop();
      else if (view === 'rankings') await renderRankings();
      else if (view === 'my') await renderMy();
      else if (view === 'admin') await renderAdmin();
    } catch (error) {
      root.innerHTML = heading('CONNECTION', '加载遇到问题') + empty(error.message || '请确认后端与数据库已启动', '确认后刷新页面重试。');
      showError(error);
    }
  }

  async function renderRoutes() {
    const params = new URLSearchParams({ page: String(state.page), size: '9' });
    if (state.query) params.set('q', state.query);
    if (state.keyword) params.set('keyword', state.keyword);
    const [routes, keywords] = await Promise.all([api(`/routes?${params}`, { auth: false }), api('/keywords', { auth: false })]);
    if (state.view !== 'routes') return;
    state.routes = routes;
    state.keywords = keywords;
    root.innerHTML = heading('EXPLORE ROUTES', '发现值得走的路线', '每一步，都是与城市的新相遇。', `第 ${state.page + 1} 页 · ${routes.length} 条路线`) +
      `<form id="search-form" class="filter-bar"><input name="q" aria-label="搜索路线" placeholder="搜索路线名称或介绍…" value="${h(state.query)}"><select name="keyword" aria-label="按关键词筛选"><option value="">全部关键词</option>${keywords.map(k => `<option value="${h(k.keywordName)}" ${k.keywordName === state.keyword ? 'selected' : ''}>${h(k.keywordName)}</option>`).join('')}</select><button class="primary-button">搜索路线 ↗</button></form>` +
      (routes.length ? `<div class="card-grid">${routes.map((r, i) => `<article class="route-card"><div class="route-cover cover-${i % 4 + 1}"><span class="tag">城市路线</span><span class="cover-trail">↝</span></div><div class="route-body"><h3>${h(r.title)}</h3><p>由 ${h(r.publisherNickname)} 分享的城市探索路线</p><div class="route-meta"><span>◷ ${num(r.recommendedMinutes)} 分钟</span><span>♡ ${num(r.favoriteCount)} 收藏</span></div><div class="route-footer"><small>${h(date(r.publishedAt))} 发布</small>${button('查看详情 ↗', 'detail', r.routeId, 'text-button')}</div></div></article>`).join('')}</div>` : empty('还没有符合条件的路线', '换个关键词试试，或发布第一条路线。')) +
      `<div class="pager"><button data-action="prev-page" ${state.page === 0 ? 'disabled' : ''}>← 上一页</button><button data-action="next-page" ${routes.length < 9 ? 'disabled' : ''}>下一页 →</button></div>`;
  }

  async function showDetail(id) {
    const r = await api(`/routes/${id}`, { auth: false });
    const points = r.points || [];
    document.querySelector('#detail-content').innerHTML = `<div class="detail-cover"><span class="eyebrow">CITY TRAIL · #${Number(r.routeId)}</span><h2>${h(r.title)}</h2></div><div class="detail-inner"><div class="detail-meta"><span class="pill">${r.routeKind === 'OFFICIAL_EVENT' ? '官方活动' : '城市路线'}</span><span class="pill slate">◷ ${num(r.recommendedMinutes)} 分钟</span>${(r.keywords || []).map(k => `<span class="pill">${h(k)}</span>`).join('')}</div><p>${h(r.description)}</p>${r.availableFrom || r.availableUntil ? `<p>开放时间：${h(date(r.availableFrom))} 至 ${h(date(r.availableUntil))}</p>` : ''}<h3>沿途点位</h3><div class="timeline">${points.map((p, i) => `<div class="timeline-row"><span class="timeline-dot">${i + 1}</span><div><strong>${h(p.pointName)}</strong><small>${num(p.latitude)}, ${num(p.longitude)}</small></div></div>`).join('')}</div><div class="detail-actions">${button('♡ 点赞', 'like', id, 'subtle-button')}${button('取消点赞', 'unlike', id, 'subtle-button')}${button('☆ 收藏', 'favorite', id, 'subtle-button')}${button('取消收藏', 'unfavorite', id, 'subtle-button')}${button('完成打卡', 'checkin', id, 'primary-button')}</div><p class="help">当前后端未提供个人点赞与收藏状态，操作结果以提示为准。</p></div>`;
    detailDialog.showModal();
  }

  async function renderMap() {
    if (state.map) { state.map.remove(); state.map = null; state.mapMarker = null; }
    const points = await api('/map-points', { auth: false });
    if (state.view !== 'map') return;
    state.points = points;
    root.innerHTML = heading('CITY MAP', '地图点位', '浏览已有点位，点击地图选坐标并新增地点。', `${points.length} 个点位`) + `<div class="map-layout"><div><div id="map-canvas" class="map-canvas"></div><p class="help">地图瓦片由 OpenStreetMap 提供。无法加载地图时也可以手动输入经纬度。</p></div><div class="map-sidebar"><div class="panel"><h3>添加地图点</h3><p class="panel-sub">点位可用于组合路线。</p><form id="point-form" class="form-grid"><label class="wide">点位名称<input name="name" maxlength="100" required placeholder="例如：城市博物馆"></label><label class="wide">地址描述<input name="address" maxlength="255" placeholder="街道与附近地标"></label><label>经度<input name="longitude" type="number" step="any" min="-180" max="180" required></label><label>纬度<input name="latitude" type="number" step="any" min="-90" max="90" required></label><button class="primary-button wide">保存点位</button></form></div><div class="panel"><h3>已有点位</h3><div class="point-scroll">${points.length ? points.map(p => `<div class="point-row"><strong>${h(p.pointName)}</strong><small>${h(p.addressText || '暂无地址')} · ${h(p.longitude)}, ${h(p.latitude)}</small></div>`).join('') : '<p class="help">暂无点位，登录后可以添加。</p>'}</div></div></div></div>`;
    if (!window.L) { document.querySelector('#map-canvas').innerHTML = `<div class="map-placeholder"><p>地图资源暂时无法加载。请在右侧手动填写经纬度。</p></div>`; return; }
    const center = points.length ? [Number(points[0].latitude), Number(points[0].longitude)] : [31.2304, 121.4737];
    state.map = L.map('map-canvas').setView(center, points.length ? 13 : 10);
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19, attribution: '&copy; OpenStreetMap contributors' }).addTo(state.map);
    for (const p of points) {
      const lat = Number(p.latitude), lon = Number(p.longitude);
      if (Number.isFinite(lat) && Number.isFinite(lon)) L.marker([lat, lon]).addTo(state.map).bindPopup(`<strong>${h(p.pointName)}</strong><br>${h(p.addressText || '')}`);
    }
    state.map.on('click', event => {
      const form = document.querySelector('#point-form');
      form.elements.longitude.value = event.latlng.lng.toFixed(6);
      form.elements.latitude.value = event.latlng.lat.toFixed(6);
      if (state.mapMarker) state.map.removeLayer(state.mapMarker);
      state.mapMarker = L.marker(event.latlng).addTo(state.map);
    });
  }

  function selectedPointMarkup() {
    const box = document.querySelector('#selected-points');
    if (!box) return;
    box.innerHTML = state.selectedPoints.length ? state.selectedPoints.map((id, i) => {
      const p = state.points.find(item => Number(item.pointId) === id);
      return `<div class="selected-item"><span><strong>${i + 1}. ${h(p?.pointName || id)}</strong><small> ${i === 0 ? '起点' : i === state.selectedPoints.length - 1 ? '终点' : '途经点'}</small></span><span>${button('↑', 'point-up', id)}${button('↓', 'point-down', id)}${button('移除', 'point-remove', id)}</span></div>`;
    }).join('') : '<p class="help">按行程顺序添加至少两个不重复的点位。</p>';
  }

  async function renderPublish() {
    const [points, keywords] = await Promise.all([api('/map-points', { auth: false }), api('/keywords', { auth: false })]);
    if (state.view !== 'publish') return;
    state.points = points; state.keywords = keywords; state.selectedPoints = []; state.selectedKeywords = [];
    const official = state.me?.roles?.includes('OFFICIAL');
    root.innerHTML = heading('SHARE A JOURNEY', '发布一条路线', '把你喜欢的街道和地点，分享给更多人。') + `<div class="two-col"><div class="panel"><h3>路线信息</h3><form id="publish-form" class="form-grid"><label class="wide">路线名称<input name="title" required maxlength="120" placeholder="例如：老城午后漫步"></label><label class="wide">路线介绍<textarea name="description" required placeholder="沿途有什么值得发现？"></textarea></label><label>推荐时长（分钟）<input name="recommendedMinutes" type="number" min="1" required value="60"></label><label>路线类型<select name="routeKind" id="route-kind"><option value="NORMAL">普通路线</option>${official ? '<option value="OFFICIAL_EVENT">官方限时活动</option>' : ''}</select></label><label class="wide">封面图片地址（可选）<input name="coverUrl" type="url" maxlength="500" placeholder="https://..."></label><label>打卡额外奖励积分<input name="checkinBonusPoints" type="number" min="0" max="1000" value="0"></label><div id="event-fields" class="wide form-grid hidden"><label>开始时间<input name="availableFrom" type="datetime-local"></label><label>结束时间<input name="availableUntil" type="datetime-local"></label></div><div class="wide"><label>关键词</label><div class="chips" id="keyword-chips">${keywords.map(k => `<button class="chip" type="button" data-action="keyword-toggle" data-id="${Number(k.keywordId)}">${h(k.keywordName)}</button>`).join('') || '<span class="help">暂无可选关键词</span>'}</div></div><button class="primary-button wide" type="submit">提交路线审核 ↗</button></form></div><div class="panel"><h3>沿途点位</h3><p class="panel-sub">先选起点，再选途经点和终点。</p><div class="field-actions"><select id="point-select" aria-label="选择点位"><option value="">选择一个地图点</option>${points.map(p => `<option value="${Number(p.pointId)}">${h(p.pointName)}</option>`).join('')}</select><button class="subtle-button" type="button" data-action="point-add">添加</button></div><p class="help">找不到想要的地点？到“地图点位”页面新增。</p><div id="selected-points" class="selected-list"></div></div></div>`;
    selectedPointMarkup();
  }

  async function renderShop() {
    const products = await api('/products', { auth: false });
    if (state.view !== 'shop') return;
    state.products = products;
    root.innerHTML = heading('POINTS STORE', '积分商城', '走过的路，可以换成一份城市纪念。', `${products.length} 件商品`) + (state.me ? `<div class="stat-strip"><div class="stat"><strong>${num(state.me.pointsBalance)}</strong><span>当前可用积分</span></div><div class="stat"><strong>${products.length}</strong><span>上架商品</span></div><div class="stat"><strong>1</strong><span>每次默认兑换数量</span></div></div>` : '') + (products.length ? `<div class="card-grid">${products.map((p, i) => `<article class="product-card"><div class="product-art">${['✳', '◇', '▣', '✿'][i % 4]}</div><h3>${h(p.productName)}</h3><p>${h(p.description || '城市探索纪念礼品')}</p><div class="product-foot"><span class="price">${num(p.pointsPrice)} <small>积分</small></span>${button(p.stockQuantity > 0 ? '立即兑换' : '已兑完', 'redeem', p.productId, 'primary-button')}</div><p class="help">库存 ${num(p.stockQuantity)} 件</p></article>`).join('')}</div>` : empty('商城还没有上架商品', '管理员添加商品后会在这里显示。'));
    root.querySelectorAll('[data-action="redeem"]').forEach(el => { if (Number(state.products.find(p => Number(p.productId) === Number(el.dataset.id))?.stockQuantity) <= 0) el.disabled = true; });
  }

  async function renderRankings() {
    const kinds = [['routes', '路线收藏榜', 'title'], ['users', '用户积分榜', 'nickname'], ['points', '热门点位榜', 'pointName']];
    const lists = await Promise.all(kinds.map(k => api(`/rankings/${k[0]}`, { auth: false })));
    if (state.view !== 'rankings') return;
    root.innerHTML = heading('CITY LEADERBOARD', '城市榜单', '看看大家最近都在探索哪里。') + `<div class="rank-grid">${kinds.map((k, index) => `<div class="rank-card"><h3>${h(k[1])}</h3><div class="rank-list">${lists[index].length ? lists[index].map((row, i) => `<div class="rank-row"><span class="rank-number">${i + 1}</span><span class="rank-name">${h(row[k[2]])}</span><span class="rank-score">${num(row.score)}</span></div>`).join('') : '<p class="help">榜单暂无数据</p>'}</div></div>`).join('')}</div>`;
  }

  async function renderMy() {
    if (!state.me) { root.innerHTML = heading('MY JOURNEY', '我的足迹') + empty('登录后查看你的路线和兑换记录', '发布路线、打卡和兑换都从这里开始。') + '<div class="field-actions"><button class="primary-button" data-action="login">立即登录</button></div>'; return; }
    const [routes, orders] = await Promise.all([api('/my/routes'), api('/my/redemptions')]);
    if (state.view !== 'my') return;
    root.innerHTML = heading('MY JOURNEY', '我的足迹', `你好，${state.me.nickname || state.me.username}。`) + `<div class="stat-strip"><div class="stat"><strong>${num(state.me.pointsBalance)}</strong><span>积分余额</span></div><div class="stat"><strong>${routes.length}</strong><span>发布路线</span></div><div class="stat"><strong>${orders.length}</strong><span>兑换订单</span></div></div><div class="two-col"><div class="panel"><h3>我发布的路线</h3><div class="plain-list">${routes.length ? routes.map(r => `<div class="plain-item"><span><strong>${h(r.title)}</strong><br><small>${status(r.routeStatus)} · ${date(r.createdAt)}</small></span>${r.routeStatus === 'PUBLISHED' ? button('关闭路线', 'close-route', r.routeId) : ''}</div>`).join('') : '<p class="help">还没有发布路线。</p>'}</div></div><div class="panel"><h3>兑换记录</h3><div class="plain-list">${orders.length ? orders.map(o => `<div class="plain-item"><span><strong>${h(o.productName)} × ${num(o.quantity)}</strong><br><small>${status(o.orderStatus)} · ${date(o.redeemedAt)} · 领取码 ${h(o.pickupCode || '待生成')}</small></span>${o.orderStatus === 'REDEEMED' ? button('取消兑换', 'cancel-order', o.redemptionId) : ''}</div>`).join('') : '<p class="help">还没有兑换记录。</p>'}</div></div></div>`;
  }

  function table(headers, rows) { return `<div class="table-wrap"><table class="data-table"><thead><tr>${headers.map(x => `<th>${h(x)}</th>`).join('')}</tr></thead><tbody>${rows.join('') || `<tr><td colspan="${headers.length}">暂无数据</td></tr>`}</tbody></table></div>`; }
  async function renderAdmin() {
    if (!state.me?.roles?.includes('ADMIN')) return;
    const [pendingUsers, pendingRoutes, users, products, orders] = await Promise.all(['/admin/users/pending', '/admin/routes/pending', '/admin/users', '/admin/products', '/admin/redemptions'].map(path => api(path)));
    if (state.view !== 'admin') return;
    root.innerHTML = heading('ADMIN WORKSPACE', '管理中心', '审核、积分和商城运营。') + `<div class="admin-stack"><div class="two-col"><div class="panel"><h3>待审核用户 · ${pendingUsers.length}</h3>${table(['账号', '昵称', '操作'], pendingUsers.map(u => `<tr><td>${h(u.username)}</td><td>${h(u.nickname)}</td><td>${button('通过', 'user-approve', u.userId)}${button('拒绝', 'user-reject', u.userId)}</td></tr>`))}</div><div class="panel"><h3>待审核路线 · ${pendingRoutes.length}</h3>${table(['路线', '发布者 ID', '操作'], pendingRoutes.map(r => `<tr><td>${h(r.title)}</td><td>${h(r.publisherId)}</td><td>${button('通过', 'route-approve', r.routeId)}${button('拒绝', 'route-reject', r.routeId)}</td></tr>`))}</div></div><div class="admin-actions"><div class="panel"><h3>添加关键词</h3><form id="keyword-form" class="inline-form"><label>关键词<input name="name" required maxlength="50" placeholder="例如：自然风景"></label><button class="primary-button">添加</button></form></div><div class="panel"><h3>添加商品</h3><form id="product-form" class="form-grid"><label>名称<input name="name" required maxlength="120"></label><label>积分价格<input name="pointsPrice" type="number" min="1" required></label><label>库存<input name="stock" type="number" min="0" required></label><label>描述<input name="description"></label><button class="primary-button wide">上架商品</button></form></div></div><div class="panel"><h3>用户管理</h3>${table(['ID / 用户', '状态', '积分与角色'], users.map(u => `<tr><td>#${h(u.userId)} ${h(u.nickname)} <small>(${h(u.username)})</small></td><td>${status(u.accountStatus)}</td><td>${button('加减积分', 'grant-points', u.userId)}${button('授予官方角色', 'grant-official', u.userId)}</td></tr>`))}</div><div class="panel"><h3>商品管理</h3>${table(['商品', '价格 / 库存', '操作'], products.map(p => `<tr><td>${h(p.productName)} <small>(${status(p.productStatus)})</small></td><td>${num(p.pointsPrice)} / ${num(p.stockQuantity)}</td><td>${button('补货', 'restock', p.productId)}${p.productStatus !== 'OFF_SALE' ? button('下架', 'off-sale', p.productId) : ''}</td></tr>`))}</div><div class="panel"><h3>兑换订单</h3>${table(['订单', '用户 / 商品', '状态', '操作'], orders.map(o => `<tr><td>#${h(o.redemptionId)}</td><td>${h(o.nickname)} · ${h(o.productName)} × ${num(o.quantity)}</td><td>${status(o.orderStatus)}</td><td>${o.orderStatus === 'REDEEMED' ? button('确认发放', 'fulfill', o.redemptionId) : ''}</td></tr>`))}</div><div class="panel"><h3>课程报告</h3><p class="panel-sub">导出路线、用户和点位排行榜 Word 报告。</p><button class="subtle-button" data-action="download-report">下载报告 .docx</button></div></div>`;
  }

  async function downloadReport() {
    const response = await fetch('/api/admin/reports/rankings.docx', { headers: { Authorization: basicHeader(...state.credentials) } });
    if (!response.ok) throw new Error(`报告下载失败（${response.status}）`);
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement('a');
    link.href = url; link.download = '城市路线排行榜.docx';
    document.body.append(link); link.click(); link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  async function act(action, id) {
    if (action === 'login') { authDialog.showModal(); return; }
    if (action === 'prev-page' || action === 'next-page') { state.page += action === 'next-page' ? 1 : -1; await renderRoutes(); return; }
    if (action === 'detail') { await showDetail(id); return; }
    if (action === 'point-add') {
      const selected = Number(document.querySelector('#point-select')?.value);
      if (!selected) return toast('请先选择一个地图点', true);
      if (state.selectedPoints.includes(selected)) return toast('同一条路线不能重复添加点位', true);
      state.selectedPoints.push(selected); selectedPointMarkup(); return;
    }
    if (action.startsWith('point-')) {
      const index = state.selectedPoints.indexOf(id);
      if (index < 0) return;
      if (action === 'point-remove') state.selectedPoints.splice(index, 1);
      else if (action === 'point-up' && index > 0) [state.selectedPoints[index - 1], state.selectedPoints[index]] = [state.selectedPoints[index], state.selectedPoints[index - 1]];
      else if (action === 'point-down' && index < state.selectedPoints.length - 1) [state.selectedPoints[index + 1], state.selectedPoints[index]] = [state.selectedPoints[index], state.selectedPoints[index + 1]];
      selectedPointMarkup(); return;
    }
    if (action === 'keyword-toggle') {
      const index = state.selectedKeywords.indexOf(id);
      if (index >= 0) state.selectedKeywords.splice(index, 1); else state.selectedKeywords.push(id);
      document.querySelector(`[data-action="keyword-toggle"][data-id="${id}"]`)?.classList.toggle('selected', index < 0);
      return;
    }
    if (!needLogin()) return;
    const routeAction = { like: ['POST', 'like'], unlike: ['DELETE', 'like'], favorite: ['POST', 'favorite'], unfavorite: ['DELETE', 'favorite'] }[action];
    if (routeAction) { await api(`/routes/${id}/${routeAction[1]}`, { method: routeAction[0] }); toast('操作成功'); return; }
    if (action === 'checkin') {
      const note = prompt('记录这次打卡（可留空）：');
      if (note === null) return;
      await api(`/routes/${id}/checkins`, { method: 'POST', body: { note } });
      detailDialog.close(); await refreshMe(); toast('打卡成功，积分已更新'); return;
    }
    if (action === 'redeem') {
      const p = state.products.find(item => Number(item.productId) === id);
      if (!confirm(`确认用 ${num(p?.pointsPrice)} 积分兑换「${p?.productName || '该商品'}」一件吗？`)) return;
      await api(`/products/${id}/redemptions`, { method: 'POST', body: { quantity: 1 } });
      await refreshMe(); await renderShop(); toast('兑换成功，请到“我的足迹”查看领取码'); return;
    }
    if (action === 'close-route' && confirm('确定关闭这条路线吗？')) { await api(`/routes/${id}/close`, { method: 'POST' }); await renderMy(); toast('路线已关闭'); return; }
    if (action === 'cancel-order' && confirm('确定取消兑换并退回积分吗？')) { await api(`/redemptions/${id}/cancel`, { method: 'POST' }); await refreshMe(); await renderMy(); toast('兑换已取消，积分已退回'); return; }
    if (action.endsWith('-approve') || action.endsWith('-reject')) {
      const [target, decision] = action.split('-');
      if (!confirm(`确认${decision === 'approve' ? '通过' : '拒绝'}该${target === 'user' ? '用户' : '路线'}吗？`)) return;
      await api(`/admin/${target === 'user' ? 'users' : 'routes'}/${id}/review?approve=${decision === 'approve'}`, { method: 'POST' });
      await renderAdmin(); toast('审核已完成'); return;
    }
    if (action === 'grant-points') {
      const points = Number(prompt('输入积分变化值，可输入负数：'));
      if (!Number.isInteger(points) || points === 0) return toast('请输入非零整数', true);
      const reason = prompt('填写积分调整原因：');
      if (!reason?.trim()) return toast('需要填写原因', true);
      await api(`/admin/users/${id}/points`, { method: 'POST', body: { points, reason: reason.trim() } });
      toast('积分已调整'); return;
    }
    if (action === 'grant-official' && confirm('确认授予该用户官方人员角色吗？')) { await api(`/admin/users/${id}/roles`, { method: 'POST', body: { role: 'OFFICIAL' } }); toast('角色已授予'); return; }
    if (action === 'restock') {
      const quantity = Number(prompt('输入补货数量：'));
      if (!Number.isInteger(quantity) || quantity < 1) return toast('请输入正整数', true);
      await api(`/admin/products/${id}/restock`, { method: 'POST', body: { quantity } }); await renderAdmin(); toast('补货完成'); return;
    }
    if (action === 'off-sale' && confirm('确定下架该商品吗？')) { await api(`/admin/products/${id}/off-sale`, { method: 'POST' }); await renderAdmin(); toast('商品已下架'); return; }
    if (action === 'fulfill' && confirm('确认奖品已发放给用户吗？')) { await api(`/admin/redemptions/${id}/fulfill`, { method: 'POST' }); await renderAdmin(); toast('订单已完成'); return; }
    if (action === 'download-report') { await downloadReport(); toast('报告已开始下载'); }
  }

  document.addEventListener('click', async event => {
    const close = event.target.closest('[data-close]');
    if (close) { document.getElementById(close.dataset.close)?.close(); return; }
    const nav = event.target.closest('[data-view]');
    if (nav) { await navigate(nav.dataset.view); return; }
    const trigger = event.target.closest('[data-action]');
    if (trigger) {
      trigger.disabled = true;
      try { await act(trigger.dataset.action, Number(trigger.dataset.id)); }
      catch (error) { showError(error); }
      finally { if (trigger.isConnected) trigger.disabled = false; }
    }
  });

  document.querySelector('#account-button').addEventListener('click', () => {
    if (!state.me) { authDialog.showModal(); return; }
    state.credentials = null; state.me = null; syncAccount();
    if (state.view === 'admin') navigate('routes'); else navigate(state.view);
    toast('已退出登录');
  });
  document.querySelector('#login-tab').addEventListener('click', () => setAuthMode('login'));
  document.querySelector('#register-tab').addEventListener('click', () => setAuthMode('register'));
  function setAuthMode(mode) {
    state.authMode = mode;
    document.querySelector('#login-tab').classList.toggle('selected', mode === 'login');
    document.querySelector('#register-tab').classList.toggle('selected', mode === 'register');
    document.querySelector('#nickname-field').classList.toggle('hidden', mode !== 'register');
    document.querySelector('#nickname-field input').required = mode === 'register';
    document.querySelector('#auth-heading').textContent = mode === 'login' ? '登录城迹' : '加入城迹';
    document.querySelector('#auth-submit').textContent = mode === 'login' ? '登录' : '提交注册';
  }

  document.querySelector('#auth-form').addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const submit = form.querySelector('[type="submit"]'); submit.disabled = true;
    const data = Object.fromEntries(new FormData(form));
    try {
      if (state.authMode === 'register') {
        await api('/auth/register', { method: 'POST', body: { username: data.username.trim(), password: data.password, nickname: data.nickname.trim() }, auth: false });
        form.reset(); setAuthMode('login'); toast('注册成功，等待管理员审核后登录');
      } else {
        state.credentials = [data.username.trim(), data.password];
        try { await refreshMe(); } catch (error) { state.credentials = null; throw error; }
        authDialog.close(); form.reset(); await navigate(state.view); toast('登录成功');
      }
    } catch (error) { showError(error); }
    finally { submit.disabled = false; }
  });

  document.addEventListener('change', event => {
    if (event.target.id !== 'route-kind') return;
    const official = event.target.value === 'OFFICIAL_EVENT';
    document.querySelector('#event-fields').classList.toggle('hidden', !official);
    document.querySelectorAll('#event-fields input').forEach(el => { el.required = official; });
  });

  document.addEventListener('submit', async event => {
    if (event.target.id === 'auth-form') return;
    event.preventDefault();
    const form = event.target;
    const submit = form.querySelector('[type="submit"],button:not([type])');
    if (submit) submit.disabled = true;
    try {
      const data = Object.fromEntries(new FormData(form));
      if (form.id === 'search-form') { state.query = data.q.trim(); state.keyword = data.keyword; state.page = 0; await renderRoutes(); }
      else if (form.id === 'point-form') {
        if (!needLogin()) return;
        await api('/map-points', { method: 'POST', body: { name: data.name.trim(), address: data.address.trim(), longitude: Number(data.longitude), latitude: Number(data.latitude) } });
        await renderMap(); toast('地图点已添加');
      } else if (form.id === 'publish-form') {
        if (!needLogin()) return;
        if (state.selectedPoints.length < 2) return toast('请至少选择两个点位', true);
        const official = data.routeKind === 'OFFICIAL_EVENT';
        if (official && new Date(data.availableUntil) <= new Date(data.availableFrom)) return toast('结束时间必须晚于开始时间', true);
        const result = await api('/routes', { method: 'POST', body: { title: data.title.trim(), description: data.description.trim(), coverUrl: data.coverUrl.trim() || null, routeKind: data.routeKind, recommendedMinutes: Number(data.recommendedMinutes), availableFrom: official ? data.availableFrom : null, availableUntil: official ? data.availableUntil : null, checkinBonusPoints: Number(data.checkinBonusPoints), pointIds: state.selectedPoints, keywordIds: state.selectedKeywords } });
        await navigate('my'); toast(`路线 #${result.routeId} 已提交，等待管理员审核`);
      } else if (form.id === 'keyword-form') { await api('/admin/keywords', { method: 'POST', body: { name: data.name.trim() } }); await renderAdmin(); toast('关键词已添加'); }
      else if (form.id === 'product-form') { await api('/admin/products', { method: 'POST', body: { name: data.name.trim(), description: data.description.trim(), pointsPrice: Number(data.pointsPrice), stock: Number(data.stock) } }); await renderAdmin(); toast('商品已上架'); }
    } catch (error) { showError(error); }
    finally { if (submit?.isConnected) submit.disabled = false; }
  });

  navigate('routes');
})();
