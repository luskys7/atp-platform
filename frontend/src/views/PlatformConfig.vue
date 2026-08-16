<template>
  <div class="page-container config-page" :class="{ 'is-solo': !!soloTab }">
    <PageHeader :title="pageTitle" :subtitle="pageSubtitle" />

    <!-- 模块 2：分组标签导航（独立入口页不展示，避免夹杂其它配置） -->
    <nav v-if="!soloTab" class="config-nav" aria-label="配置分组导航">
      <div class="nav-groups">
        <el-tooltip
          v-for="group in visibleNavGroups"
          :key="group.id"
          :content="group.tip"
          placement="top"
          :show-after="300"
        >
          <button
            type="button"
            class="nav-group-chip"
            :class="{ active: activeGroupId === group.id }"
            @click="toggleGroup(group.id)"
          >
            <el-icon class="nav-fold-icon">
              <component :is="activeGroupId === group.id && !groupTabsCollapsed ? ArrowDown : ArrowRight" />
            </el-icon>
            <span class="nav-group-label">{{ group.title }}</span>
            <span class="nav-count">{{ group.tabs.length }}</span>
          </button>
        </el-tooltip>
      </div>
      <div v-show="!groupTabsCollapsed && activeGroupTabs.length" class="nav-tabs">
        <el-tooltip
          v-for="tab in activeGroupTabs"
          :key="tab.name"
          :content="tab.hint"
          placement="bottom"
          :show-after="200"
        >
          <button
            type="button"
            class="nav-tab"
            :class="{ active: activeTab === tab.name }"
            @click="selectTab(tab.name)"
          >{{ tab.label }}</button>
        </el-tooltip>
      </div>
    </nav>

    <!-- 模块 3：模块专属统计 -->
    <div class="stats-row">
      <div
        v-for="card in moduleStats"
        :key="card.key"
        class="stat-card"
        :class="card.tone"
        @click="card.onClick && card.onClick()"
      >
        <div class="stat-value" :class="card.valueClass">{{ card.value }}</div>
        <div class="stat-label">{{ card.label }}</div>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="core-tabs core-tabs--headless" @tab-change="onTabChange">
      <el-tab-pane v-if="showPane('env')" label="环境配置" name="env">
        <AppCard :hover="false">
          <div style="margin-bottom:12px"><el-button type="primary" size="small" @click="openEnv()">添加环境</el-button></div>
          <el-table :data="envs" stripe size="small">
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="env_type" label="类型" width="120">
              <template #default="{ row }">{{ envTypeLabel(row.env_type) }}</template>
            </el-table-column>
            <el-table-column prop="base_url" label="接口基础域名地址" show-overflow-tooltip />
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button size="small" type="primary" plain @click="openEnv(row)">编辑</el-button>
                <el-button size="small" type="danger" plain @click="envApi.delete(row.id).then(loadEnvs)">禁用</el-button>
              </template>
            </el-table-column>
          </el-table>
        </AppCard>
      </el-tab-pane>

      <el-tab-pane v-if="showPane('dataset')" label="数据集" name="dataset">
        <AppCard :hover="false">
          <div style="margin-bottom:12px"><el-button type="primary" size="small" @click="openDataset()">添加数据集</el-button></div>
          <el-table :data="datasets" stripe size="small">
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="description" label="描述" show-overflow-tooltip />
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <el-button size="small" type="primary" plain @click="openDataset(row)">编辑</el-button>
                <el-button size="small" type="danger" plain @click="deleteDataset(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </AppCard>
      </el-tab-pane>

      <el-tab-pane v-if="showPane('steps')" label="公共步骤" name="steps">
        <AppCard :hover="false" class="steps-card">
          <div class="toolbar-row">
            <el-button v-if="userStore.isAdmin" type="primary" @click="openStep()">+ 快速新建</el-button>
            <el-button v-if="userStore.isAdmin" @click="showStepTemplates = true">模板库</el-button>
            <el-tooltip content="请先勾选条目后执行批量操作" :disabled="hasStepSelection" placement="top">
              <span class="batch-wrap">
                <el-button class="btn-muted" :disabled="!hasStepSelection || !userStore.isAdmin" @click="openStepTransfer">移交选中</el-button>
                <el-button class="btn-muted" :disabled="!hasStepSelection || !userStore.isAdmin" @click="batchCopySteps">批量复制</el-button>
                <el-button class="btn-muted" :disabled="!hasStepSelection || !userStore.isAdmin" @click="batchSetStepStatus('active')">批量启用</el-button>
                <el-button class="btn-muted" :disabled="!hasStepSelection || !userStore.isAdmin" @click="batchSetStepStatus('deprecated')">批量停用</el-button>
                <el-button class="btn-muted" type="danger" plain :disabled="!hasStepSelection || !userStore.isAdmin" @click="batchDeleteSteps">批量删除</el-button>
              </span>
            </el-tooltip>
          </div>

          <div class="filter-bar steps-filter">
            <el-input
              v-model="stepFilters.keyword"
              placeholder="搜索公共步骤名称、描述"
              clearable
              style="width:260px"
              @clear="stepPage = 1"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="stepFilters.category" placeholder="大类" clearable style="width:160px" @change="stepPage = 1">
              <el-option label="全部大类" value="" />
              <el-option v-for="c in stepCategoryOptions" :key="c.key" :label="c.label" :value="c.key" />
            </el-select>
            <el-select v-model="stepFilters.platform" placeholder="适用端" clearable style="width:120px" @change="stepPage = 1">
              <el-option label="全部端" value="" />
              <el-option v-for="p in stepPlatformOptions" :key="p.key" :label="p.label" :value="p.key" />
            </el-select>
            <el-select v-model="stepFilters.status" placeholder="使用状态" clearable style="width:140px" @change="stepPage = 1">
              <el-option label="全部" value="" />
              <el-option label="已启用" value="active" />
              <el-option label="已停用" value="deprecated" />
            </el-select>
            <div class="filter-right">
              <el-button type="primary" plain :loading="stepsLoading" @click="refreshSteps">
                <el-icon><Refresh /></el-icon> 刷新列表
              </el-button>
              <el-button @click="resetStepFilters">重置筛选条件</el-button>
            </div>
          </div>

          <el-table
            :data="pagedSteps"
            v-loading="stepsLoading"
            stripe
            size="small"
            empty-text=""
            @selection-change="onStepSelectionChange"
          >
            <el-table-column v-if="userStore.isAdmin" type="selection" width="48" />
            <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
            <el-table-column label="大类" width="130">
              <template #default="{ row }">{{ stepCategoryLabel(row) }}</template>
            </el-table-column>
            <el-table-column label="适用端" width="90">
              <template #default="{ row }">{{ stepPlatformLabel(row) }}</template>
            </el-table-column>
            <el-table-column prop="description" label="功能描述" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.description || '—' }}</template>
            </el-table-column>
            <el-table-column label="使用状态" width="110">
              <template #default="{ row }">
                <el-tag
                  size="small"
                  effect="plain"
                  :type="row.status === 'active' ? 'success' : 'info'"
                >
                  {{ row.status === 'active' ? '已启用' : '已停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="380" fixed="right">
              <template #default="{ row }">
                <div class="row-actions">
                  <el-button size="small" class="btn-muted-sm" @click="viewStepDetail(row)">查看步骤</el-button>
                  <el-button v-if="userStore.isAdmin" size="small" type="primary" @click="openStep(row)">编辑</el-button>
                  <el-button v-if="userStore.isAdmin" size="small" class="btn-muted-sm" @click="copyStep(row)">复制</el-button>
                  <el-button
                    v-if="userStore.isAdmin"
                    size="small"
                    type="warning"
                    @click="toggleStepStatus(row)"
                  >{{ row.status === 'active' ? '停用' : '启用' }}</el-button>
                  <el-button v-if="userStore.isAdmin" size="small" type="danger" @click="deleteStep(row)">删除</el-button>
                  <el-button size="small" plain @click="showStepComments(row)">批注</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="!stepsLoading && !filteredSteps.length" class="table-empty">
            <p class="empty-title">暂无公共复用步骤</p>
            <div class="empty-actions">
                  <el-button v-if="userStore.isAdmin" type="primary" @click="openStep()">快速新建</el-button>
              <el-button v-if="userStore.isAdmin" @click="importStepTemplates">导入步骤模板</el-button>
            </div>
            <p class="empty-hint">公共步骤可在测试用例、套件钩子内一键复用，减少重复编写操作流程。</p>
          </div>

          <div class="pager-bar">
            <div class="pager-stats">当前筛选结果共 <strong>{{ filteredSteps.length }}</strong> 条公共步骤</div>
            <el-pagination
              v-model:current-page="stepPage"
              v-model:page-size="stepPageSize"
              :total="filteredSteps.length"
              :page-sizes="[10, 15, 20, 50]"
              layout="sizes, prev, pager, next"
            />
          </div>
        </AppCard>
      </el-tab-pane>

      <el-tab-pane v-if="showPane('recording')" label="录屏配置" name="recording">
        <AppCard title="录屏运行时配置" :hover="false" v-loading="recordingFeaturesLoading">
          <div style="margin-bottom:12px;display:flex;align-items:center;gap:8px">
            <el-tag :type="recordingFeatures.source === 'runtime' ? 'warning' : 'info'" size="small">
              {{ recordingFeatures.source === 'runtime' ? '运行时覆盖' : 'YAML 默认值' }}
            </el-tag>
            <span class="form-hint">保存后立即生效，无需重启后端</span>
          </div>
          <el-form :model="recordingForm" label-width="140px" size="small" style="max-width:520px">
            <el-form-item label="recording_v2">
              <el-switch v-model="recordingForm.recording_v2" :disabled="!userStore.isAdmin" />
              <span class="form-hint" style="margin-left:8px">关闭时隐藏全局 FAB 与录制状态栏</span>
            </el-form-item>
            <el-form-item label="识别率阈值 (%)">
              <el-input-number v-model="recordingForm.min_recognition_rate" :min="50" :max="100" :step="1"
                :disabled="!userStore.isAdmin" />
            </el-form-item>
            <el-form-item label="定位命中阈值 (%)">
              <el-input-number v-model="recordingForm.min_locator_hit_rate" :min="50" :max="100" :step="1"
                :disabled="!userStore.isAdmin" />
            </el-form-item>
            <el-form-item label="CPU 长任务上限">
              <el-input-number v-model="recordingForm.max_long_tasks_per_min" :min="1" :max="20" :step="1"
                :disabled="!userStore.isAdmin" />
              <span class="form-hint" style="margin-left:8px">次/分钟</span>
            </el-form-item>
            <el-form-item v-if="userStore.isAdmin">
              <el-button type="primary" :loading="recordingFeaturesSaving" @click="saveRecordingFeatures">保存</el-button>
              <el-button :loading="recordingFeaturesSaving" @click="resetRecordingFeatures">恢复 YAML 默认</el-button>
            </el-form-item>
          </el-form>
          <el-button type="primary" link @click="$router.push('/recording-quality')">打开录制质量报告</el-button>
        </AppCard>
      </el-tab-pane>

      <el-tab-pane v-if="showPane('schedule')" label="定时调度" name="schedule">
        <AppCard :hover="false">
          <div style="margin-bottom:12px"><el-button type="primary" size="small" @click="openSchedule()">添加定时任务</el-button></div>
          <el-table :data="schedules" stripe size="small">
            <el-table-column prop="name" label="任务名称" min-width="160" show-overflow-tooltip />
            <el-table-column label="回归套件" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ scheduleSuiteLabel(row.suite_id) }}</template>
            </el-table-column>
            <el-table-column prop="cron_expression" label="调度周期" width="150" show-overflow-tooltip />
            <el-table-column prop="enabled" label="状态" width="90">
              <template #default="{ row }">
                <el-switch :model-value="row.enabled" @change="v => scheduleApi.toggle(row.id, v).then(loadSchedules)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <el-button size="small" type="primary" plain @click="openSchedule(row)">编辑</el-button>
                <el-button size="small" type="danger" plain @click="deleteSchedule(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </AppCard>
      </el-tab-pane>

      <el-tab-pane v-if="showPane('teams') && userStore.isAdmin" label="团队空间" name="teams">
        <AppCard :hover="false">
          <div style="margin-bottom:12px"><el-button type="primary" size="small" @click="openTeam()">新建团队</el-button></div>
          <el-table :data="teams" stripe size="small">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="code" label="编码" width="120" />
            <el-table-column prop="description" label="描述" show-overflow-tooltip />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button size="small" type="primary" plain @click="openTeam(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
          <p class="tab-hint">用户归属团队后，用例/任务/设备/套件按团队隔离；超级管理员可跨团队查看</p>
          <el-divider />
          <div class="section-title">用户团队分配</div>
          <el-table :data="userOptions" stripe size="small">
            <el-table-column prop="username" label="用户名" width="120" />
            <el-table-column prop="display_name" label="显示名" />
            <el-table-column prop="role" label="角色" width="120" />
            <el-table-column label="团队" width="200">
              <template #default="{ row }">
                <el-select :model-value="row.team_id" placeholder="选择团队" size="small" style="width:100%"
                  @change="v => assignUserTeam(row.id, v)">
                  <el-option v-for="t in teams" :key="t.id" :label="t.name" :value="t.id" />
                </el-select>
              </template>
            </el-table-column>
          </el-table>
        </AppCard>
      </el-tab-pane>

      <el-tab-pane v-if="showPane('baseline')" label="版本基线" name="baseline">
        <AppCard :hover="false">
          <div style="margin-bottom:12px"><el-button v-if="userStore.isAdmin" type="primary" size="small" @click="openBaseline()">添加基线</el-button></div>
          <el-table :data="baselines" stripe size="small">
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="version_label" label="版本标签" width="120" />
            <el-table-column prop="app_package_id" label="APP包ID" width="90" />
            <el-table-column prop="suite_id" label="套件ID" width="80" />
            <el-table-column prop="env_id" label="环境ID" width="80" />
            <el-table-column label="操作" width="220">
              <template #default="{ row }">
                <el-button v-if="userStore.isAdmin" size="small" type="primary" plain @click="openBaseline(row)">编辑</el-button>
                <el-button size="small" type="warning" plain @click="compareBaseline(row)">比对</el-button>
                <el-button v-if="userStore.isAdmin" size="small" type="danger" plain @click="archiveBaseline(row)">归档</el-button>
              </template>
            </el-table-column>
          </el-table>
        </AppCard>
      </el-tab-pane>

      <el-tab-pane v-if="showPane('global-params') && userStore.isAdmin" label="全局参数" name="global-params">
        <AppCard :hover="false">
          <div style="margin-bottom:12px"><el-button type="primary" size="small" @click="openGlobalParam()">添加参数</el-button></div>
          <el-table :data="globalParams" stripe size="small">
            <el-table-column prop="param_key" label="参数键" width="160" />
            <el-table-column prop="scope" label="作用域" width="110">
              <template #default="{ row }">{{ scopeLabel(row.scope) }}</template>
            </el-table-column>
            <el-table-column prop="param_value" label="值" show-overflow-tooltip />
            <el-table-column prop="enabled" label="启用" width="70">
              <template #default="{ row }">
                <el-switch :model-value="row.enabled" @change="v => toggleGlobalParam(row, v)" />
              </template>
            </el-table-column>
            <el-table-column prop="version_num" label="版本" width="70" />
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button size="small" type="primary" plain @click="openGlobalParam(row)">编辑</el-button>
                <el-button size="small" plain @click="showGlobalParamLogs(row)">日志</el-button>
              </template>
            </el-table-column>
          </el-table>
          <p class="tab-hint">作用域优先级：团队私有 &gt; 项目专属 &gt; 平台全局；脚本内使用 &#123;&#123;参数键&#125;&#125; 引用；敏感参数 AES 加密存储</p>
        </AppCard>
      </el-tab-pane>

      <el-tab-pane v-if="showPane('assert-policy') && userStore.isAdmin" label="断言策略" name="assert-policy">
        <AppCard :hover="false">
          <div style="margin-bottom:12px"><el-button type="primary" size="small" @click="openAssertPolicy()">添加规则</el-button></div>
          <el-table :data="assertPolicies" stripe size="small">
            <el-table-column prop="rule_type" label="规则类型" width="150">
              <template #default="{ row }">{{ row.rule_type === 'whitelist' ? '白名单 (失败跳过)' : '黑名单 (强制阻断)' }}</template>
            </el-table-column>
            <el-table-column prop="target_type" label="目标类型" width="110">
              <template #default="{ row }">{{ assertTargetLabel(row.target_type) }}</template>
            </el-table-column>
            <el-table-column prop="pattern" label="匹配模式" show-overflow-tooltip />
            <el-table-column prop="description" label="说明" show-overflow-tooltip />
            <el-table-column prop="enabled" label="启用" width="70">
              <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '已启用' : '已停用' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <el-button size="small" type="primary" plain @click="openAssertPolicy(row)">编辑</el-button>
                <el-button size="small" type="danger" plain @click="deleteAssertPolicy(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <p class="tab-hint">白名单：断言失败仅记日志并继续；黑名单：匹配失败立即阻断用例 / 套件。匹配模式支持 * 通配与英文逗号多关键词。</p>
        </AppCard>
      </el-tab-pane>

      <el-tab-pane v-if="showPane('data-factory') && userStore.isAdmin" label="动态造数" name="data-factory">
        <AppCard :hover="false">
          <div style="margin-bottom:12px"><el-button type="primary" size="small" @click="openDataFactory()">添加模板</el-button></div>
          <el-table :data="dataFactoryTemplates" stripe size="small">
            <el-table-column prop="name" label="模板名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="method" label="请求方式" width="100">
              <template #default="{ row }">{{ methodLabel(row.method) }}</template>
            </el-table-column>
            <el-table-column prop="url_template" label="接口地址" show-overflow-tooltip />
            <el-table-column prop="enabled" label="启用" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '已启用' : '已停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button size="small" type="primary" plain @click="openDataFactory(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
          <p class="tab-hint">用例步骤「动态造数」调用模板；任务完成后自动触发脏数据清理 API</p>
        </AppCard>
      </el-tab-pane>

      <el-tab-pane v-if="showPane('credentials') && userStore.isAdmin" label="加密凭据" name="credentials">
        <AppCard :hover="false">
          <div style="margin-bottom:12px"><el-button type="primary" size="small" @click="openCredential()">添加凭据</el-button></div>
          <el-table :data="credentials" stripe size="small">
            <el-table-column prop="name" label="凭据名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="category" label="分类" width="120">
              <template #default="{ row }">{{ credentialCategoryLabel(row.category) }}</template>
            </el-table-column>
            <el-table-column prop="visibility_scope" label="可见权限" width="100">
              <template #default="{ row }">{{ credentialScopeLabel(row.visibility_scope) }}</template>
            </el-table-column>
            <el-table-column prop="value_masked" label="密钥" width="120" />
            <el-table-column prop="env_id" label="绑定环境" width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ credentialEnvLabel(row.env_id) }}</template>
            </el-table-column>
            <el-table-column prop="description" label="描述" show-overflow-tooltip />
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <el-button size="small" type="primary" plain @click="openCredential(row)">编辑</el-button>
                <el-button size="small" type="danger" plain @click="deleteCredential(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <p class="tab-hint">脚本内以 &#123;&#123;凭据名称&#125;&#125; 引用；执行时 AES 解密注入，日志永久脱敏</p>
        </AppCard>
      </el-tab-pane>

      <el-tab-pane v-if="showPane('backup') && userStore.isAdmin" label="灾备备份" name="backup">
        <AppCard :hover="false">
          <div style="margin-bottom:12px;display:flex;gap:8px">
            <el-button type="primary" size="small" @click="createBackup" :loading="backupCreating">立即备份</el-button>
            <el-button size="small" @click="installPortableSeed" :loading="seedInstalling">导入便携种子</el-button>
            <el-button size="small" @click="loadBackups">刷新</el-button>
          </div>
          <el-table :data="backups" stripe size="small">
            <el-table-column prop="filename" label="文件名" min-width="200" />
            <el-table-column prop="size" label="大小" width="100">
              <template #default="{ row }">{{ row.size ? (row.size / 1024).toFixed(1) + ' KB' : '-' }}</template>
            </el-table-column>
            <el-table-column prop="created_at" label="时间" width="180">
              <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button size="small" type="primary" plain @click="restoreBackup(row)">还原</el-button>
                <el-button size="small" plain @click="downloadBackup(row)">下载</el-button>
                <el-button size="small" type="danger" plain @click="deleteBackup(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <p class="tab-hint">
            每日 02:00 自动全量备份（用例/目录/套件/公共步骤/控件池/环境/数据集/凭据/账号），保留 30 天。
            换机或同事开箱：用「立即备份」后下载 zip；或仓库内
            <code>fixtures/portable-seed/atp_portable_seed.zip</code>
            / <code>data/seed/</code>，空库启动会自动导入。
          </p>
        </AppCard>
      </el-tab-pane>

      <el-tab-pane v-if="showPane('monitor') && userStore.isAdmin" label="健康监控" name="monitor">
        <AppCard :hover="false">
          <HealthMonitorPanel
            :active="activeTab === 'monitor'"
            @updated="onMonitorUpdated"
          />
        </AppCard>
      </el-tab-pane>

      <el-tab-pane v-if="showPane('audit') && userStore.isAdmin" label="安全审计" name="audit">
        <AppCard :hover="false">
          <AuditLogPanel
            :active="activeTab === 'audit'"
            @updated="onAuditUpdated"
          />
        </AppCard>
      </el-tab-pane>

      <el-tab-pane v-if="showPane('accounts')" label="账号池" name="accounts">
        <AppCard :hover="false">
          <div style="margin-bottom:12px"><el-button v-if="userStore.isAdmin" type="primary" size="small" @click="openAccount()">添加账号</el-button></div>
          <el-table :data="accounts" stripe size="small">
            <el-table-column prop="username" label="用户名" width="140" />
            <el-table-column prop="password_masked" label="密码" width="100" />
            <el-table-column prop="phone_masked" label="手机号" width="130" />
            <el-table-column prop="tags" label="标签" min-width="120" show-overflow-tooltip />
            <el-table-column prop="project_key" label="所属项目" width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ accountProjectLabel(row.project_key) }}</template>
            </el-table-column>
            <el-table-column prop="team_id" label="所属团队" width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ accountTeamLabel(row.team_id) }}</template>
            </el-table-column>
            <el-table-column label="启用" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="row.enabled !== false && row.status !== 'archived' ? 'success' : 'info'">
                  {{ row.enabled !== false && row.status !== 'archived' ? '已启用' : '已冻结' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'active' ? 'success' : row.status === 'locked' ? 'warning' : 'info'">{{ { active: '空闲', locked: '占用', archived: '归档' }[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button v-if="userStore.isAdmin" size="small" type="primary" plain @click="openAccount(row)">编辑</el-button>
                <el-button v-if="userStore.isAdmin && row.status === 'locked'" size="small" plain @click="accountApi.release(row.id).then(loadAccounts)">释放</el-button>
                <el-button v-if="userStore.isAdmin && row.status !== 'archived'" size="small" plain @click="accountApi.archive(row.id).then(loadAccounts)">归档</el-button>
                <el-button v-if="userStore.isAdmin" size="small" type="danger" plain @click="deleteAccount(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </AppCard>
      </el-tab-pane>

      <el-tab-pane v-if="showPane('recycle')" label="回收站" name="recycle">
        <AppCard :hover="false">
          <RecycleBinPanel
            :active="activeTab === 'recycle'"
            @updated="onRecycleUpdated"
          />
        </AppCard>
      </el-tab-pane>
    </el-tabs>

    <!-- 模块 7：底部辅助指引（公共步骤） -->
    <section v-if="showPane('steps') && activeTab === 'steps'" class="guide-bar">
      <div class="guide-left">
        <h4>公共步骤使用指引</h4>
        <ul>
          <li>
            <strong>快速教程：</strong>
            <el-button type="primary" link @click="showStepTutorial">点击查看图文教程</el-button>
            ，学习如何在测试用例中引用公共步骤
          </li>
          <li>
            <strong>模板导入：</strong>
            <el-button type="primary" link :disabled="!userStore.isAdmin" @click="importStepTemplates">导入通用登录、清理缓存等行业标准步骤模板</el-button>
            ，一键批量创建
          </li>
        </ul>
      </div>
      <div class="guide-right">
        <h4>跨页面快捷跳转</h4>
        <div class="guide-actions">
          <el-button @click="$router.push('/cases')">前往测试用例</el-button>
          <el-button @click="$router.push('/suites')">前往测试套件</el-button>
          <el-button type="primary" plain @click="$router.push('/common-steps')">公共步骤</el-button>
        </div>
        <p class="guide-hint">编辑用例时可拖拽引用公共步骤；套件可配置前置 / 后置钩子调用复用步骤。</p>
      </div>
    </section>

    <EnvEditorDialog
      v-if="!soloTab || soloTab === 'env'"
      v-model="showEnvDialog"
      :edit-row="editingEnvRow"
      @saved="loadEnvs"
    />

    <DatasetEditorDialog
      v-if="!soloTab || soloTab === 'dataset'"
      v-model="showDatasetDialog"
      :edit-row="editingDatasetRow"
      @saved="loadDatasets"
    />

    <el-dialog v-if="showPane('steps')" v-model="showStepTemplates" title="高频公共步骤模板" width="520px">
      <div class="tpl-grid">
        <button
          v-for="t in stepTemplateList"
          :key="t.id"
          type="button"
          class="tpl-card"
          @click="createFromTemplate(t.id)"
        >
          <strong>{{ t.name }}</strong>
          <span>{{ t.keywords?.join(' · ') }}</span>
        </button>
      </div>
    </el-dialog>

    <ScheduleEditorDialog
      v-if="!soloTab || soloTab === 'schedule'"
      v-model="showScheduleDialog"
      :edit-row="editingScheduleRow"
      @saved="loadSchedules"
    />

    <TestAccountEditorDialog
      v-if="!soloTab || soloTab === 'accounts'"
      v-model="showAccountDialog"
      :edit-row="editingAccountRow"
      @saved="loadAccounts"
    />

    <el-dialog v-if="showPane('steps')" v-model="showCommentDialog" :title="`协同批注 · ${commentAssetName}`" width="560px">
      <div class="comment-list">
        <div v-if="!comments.length" class="comment-empty">暂无批注</div>
        <div v-for="c in comments" :key="c.id" class="comment-item">
          <div class="comment-meta">
            <span class="comment-author">{{ c.author_name }}</span>
            <span class="comment-time">{{ fmtTime(c.created_at) }}</span>
            <el-button v-if="c.user_id === userStore.user?.id || userStore.isAdmin" size="small" type="danger" plain @click="deleteComment(c)">删除</el-button>
          </div>
          <div class="comment-body">{{ c.content }}</div>
        </div>
      </div>
      <el-input v-model="newComment" type="textarea" :rows="3" placeholder="输入批注..." style="margin-top:12px" />
      <template #footer>
        <el-button @click="showCommentDialog = false">关闭</el-button>
        <el-button type="primary" :disabled="!newComment.trim()" @click="submitComment">发表</el-button>
      </template>
    </el-dialog>

    <CommonStepViewDialog
      v-if="showPane('steps')"
      v-model="showStepViewDialog"
      :step-row="viewingStepRow"
      :can-edit="userStore.isAdmin"
    />

    <BaselineEditorDialog
      v-if="!soloTab || soloTab === 'baseline'"
      v-model="showBaselineDialog"
      :edit-row="editingBaselineRow"
      @saved="loadBaselines"
    />

    <TeamEditorDialog
      v-if="!soloTab || soloTab === 'teams'"
      v-model="showTeamDialog"
      :edit-row="editingTeamRow"
      @saved="onTeamSaved"
    />

    <el-dialog v-model="showBaselineCompareDialog" title="基线比对" width="640px">
      <div v-if="baselineCompareResult">
        <p><strong>{{ baselineCompareResult.baseline_name }}</strong> · {{ baselineCompareResult.version_label }}</p>
        <el-tag :type="baselineCompareResult.has_diff ? 'warning' : 'success'" size="small">
          {{ baselineCompareResult.has_diff ? '与当前环境存在差异' : '与当前环境一致' }}
        </el-tag>
        <div v-if="baselineCompareResult.diffs?.length" style="margin-top:12px">
          <div v-for="(d, i) in baselineCompareResult.diffs" :key="i" class="diff-row">
            <strong>{{ d.field }}</strong>：期望 {{ d.expected ?? '-' }}，当前 {{ d.actual ?? '-' }}
          </div>
        </div>
        <pre v-if="baselineCompareResult.current_snapshot" style="margin-top:12px;font-size:12px;max-height:240px;overflow:auto">{{ JSON.stringify(baselineCompareResult.current_snapshot, null, 2) }}</pre>
      </div>
    </el-dialog>

    <GlobalParamEditorDialog
      v-if="showPane('global-params')"
      v-model="showGlobalParamDialog"
      :edit-row="editingGlobalParamRow"
      @saved="loadGlobalParams"
    />

    <el-dialog v-if="showPane('global-params')" v-model="showGlobalParamLogDialog" title="参数变更日志" width="640px">
      <el-table :data="globalParamLogs" stripe size="small">
        <el-table-column prop="version_num" label="版本" width="70" />
        <el-table-column prop="before_value" label="变更前" show-overflow-tooltip />
        <el-table-column prop="after_value" label="变更后" show-overflow-tooltip />
        <el-table-column prop="change_note" label="说明" width="120" />
        <el-table-column prop="created_at" label="时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <AssertPolicyEditorDialog
      v-if="!soloTab || soloTab === 'assert-policy'"
      v-model="showAssertPolicyDialog"
      :edit-row="editingAssertPolicyRow"
      @saved="loadAssertPolicies"
    />

    <DataFactoryEditorDialog
      v-if="!soloTab || soloTab === 'data-factory'"
      v-model="showDataFactoryDialog"
      :edit-row="editingDataFactoryRow"
      @saved="loadDataFactoryTemplates"
    />

    <CredentialEditorDialog
      v-if="!soloTab || soloTab === 'credentials'"
      v-model="showCredentialDialog"
      :edit-row="editingCredentialRow"
      @saved="loadCredentials"
    />

    <el-dialog v-if="showPane('steps')" v-model="showTransferDialog" title="资产移交" width="420px">
      <el-form label-width="90px">
        <el-form-item label="新负责人">
          <el-select v-model="transferOwnerId" filterable placeholder="选择用户" style="width:100%">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.display_name || u.username" :value="u.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="btn-cancel" @click="showTransferDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmStepTransfer">确认移交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-if="showPane('steps')" v-model="showTutorialDialog" title="公共步骤引用教程" width="640px" class="tutorial-dialog">
      <ol class="tutorial-list">
        <li>
          <div class="tutorial-title">创建并保存可复用流程</div>
          <p>在「公共步骤」页点击「快速新建」，填写名称、功能描述与步骤内容。</p>
          <p>建议优先封装高频流程，例如：通用登录、清理缓存、应用初始化、切换账号等。</p>
          <p>保存后状态为「已启用」，即可被用例与套件引用；不需要时可随时停用，停用后引用处会提示不可使用。</p>
        </li>
        <li>
          <div class="tutorial-title">在测试用例中添加调用节点</div>
          <p>前往「测试用例」→ 打开可视化用例编辑器。</p>
          <p>在步骤列表中新增节点，选择类型为「调用公共步骤」（invoke_common）。</p>
          <p>也可从侧栏「公共步骤」进入管理页，再跳转到用例编辑边写边引用。</p>
        </li>
        <li>
          <div class="tutorial-title">按名称选择并一键复用</div>
          <p>在调用节点中按名称选择已启用的公共步骤（引用按名称匹配，请勿随意改名）。</p>
          <p>执行用例时，系统会按顺序展开公共步骤内的全部操作，无需在每条用例中重复编写。</p>
          <p>若公共步骤被停用或删除，执行 / 引用时会给出不可用提示，请先恢复启用或更换步骤。</p>
        </li>
        <li>
          <div class="tutorial-title">在测试套件钩子中复用</div>
          <p>前往「测试套件」→ 编辑套件 → 配置前置钩子（setup）或后置钩子（teardown）。</p>
          <p>在钩子脚本 / 可视化步骤中同样可调用公共步骤，适合套件级登录、环境准备、数据清理等场景。</p>
          <p>一套公共步骤可同时服务多条用例与多个套件，减少重复维护成本。</p>
        </li>
      </ol>
      <p class="tutorial-tip">提示：可先用底部「导入步骤模板」快速生成登录 / 清理等标准模板，再按业务微调。</p>
      <template #footer>
        <el-button @click="$router.push('/cases')">前往测试用例</el-button>
        <el-button @click="$router.push('/suites')">前往测试套件</el-button>
        <el-button type="primary" @click="showTutorialDialog = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, ArrowRight, Refresh, Search } from '@element-plus/icons-vue'
import { envApi, datasetApi, commonStepApi, scheduleApi, recycleApi, suiteApi, authApi, accountApi, commentApi, baselineApi, auditApi, credentialApi, backupApi, monitorApi, teamApi, globalParamApi, assertPolicyApi, dataFactoryApi, recordingApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { invalidateRecordingFeatures } from '@/composables/useRecordingFeatures'
import { formatTime as fmtTime } from '@/utils/status'
import { ElMessage, ElMessageBox } from 'element-plus'
import EnvEditorDialog from '@/components/EnvEditorDialog.vue'
import TeamEditorDialog from '@/components/TeamEditorDialog.vue'
import DatasetEditorDialog from '@/components/DatasetEditorDialog.vue'
import BaselineEditorDialog from '@/components/BaselineEditorDialog.vue'
import GlobalParamEditorDialog from '@/components/GlobalParamEditorDialog.vue'
import {
  CATEGORIES as stepCategoryOptions,
  PLATFORMS as stepPlatformOptions,
  TEMPLATES as stepTemplateList,
  categoryLabel,
  platformLabel,
  extractMetaFromRow
} from '@/config/commonStepCatalog'
import AssertPolicyEditorDialog from '@/components/AssertPolicyEditorDialog.vue'
import ScheduleEditorDialog from '@/components/ScheduleEditorDialog.vue'
import DataFactoryEditorDialog from '@/components/DataFactoryEditorDialog.vue'
import TestAccountEditorDialog from '@/components/TestAccountEditorDialog.vue'
import CredentialEditorDialog from '@/components/CredentialEditorDialog.vue'
import HealthMonitorPanel from '@/components/HealthMonitorPanel.vue'
import AuditLogPanel from '@/components/AuditLogPanel.vue'
import RecycleBinPanel from '@/components/RecycleBinPanel.vue'
import CommonStepViewDialog from '@/components/common-step/CommonStepViewDialog.vue'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()

/** 侧栏独立入口：仅展示对应模块，不夹杂其它平台配置 */
const SOLO_TAB_META = {
  steps: {
    title: '公共步骤',
    subtitle: '封装登录、清理等可复用操作流程，供用例与套件一键引用'
  },
  'global-params': {
    title: '全局参数',
    subtitle: '平台 / 项目 / 团队三级变量统一注入执行上下文'
  }
}

const soloTab = computed(() => {
  const meta = route.meta?.soloTab
  return typeof meta === 'string' && meta ? meta : ''
})

const pageTitle = computed(() => SOLO_TAB_META[soloTab.value]?.title || '平台配置')
const pageSubtitle = computed(() => SOLO_TAB_META[soloTab.value]?.subtitle
  || '集中维护数据集、断言、录屏、账号池、团队基线与运维配置；公共步骤 / 全局参数请从左侧导航进入')

function showPane(name) {
  if (soloTab.value) return soloTab.value === name
  // 平台配置入口不再内嵌公共步骤 / 全局参数（已拆到侧栏独立页）
  return name !== 'steps' && name !== 'global-params'
}

const TAB_ALLOW = new Set([
  'env', 'dataset', 'steps', 'recording', 'schedule', 'teams', 'baseline',
  'global-params', 'assert-policy', 'data-factory', 'credentials', 'backup',
  'monitor', 'audit', 'accounts', 'recycle'
])
const activeTab = ref('dataset')

const NAV_GROUPS = [
  {
    id: 'reuse',
    title: '环境与复用组件',
    tip: '数据集与断言策略等复用配置；公共步骤 / 全局参数请从左侧导航进入',
    tabs: [
      // 侧栏独立页：公共步骤、全局参数 —— 不在此重复展示
      { name: 'env', label: '环境配置', hint: '管理测试 / 预发 / 生产等执行环境与 Base URL', adminOnly: false },
      { name: 'dataset', label: '数据集', hint: '维护参数化测试数据，支持多行数据驱动', adminOnly: false },
      { name: 'steps', label: '公共步骤', hint: '封装登录、清理等可复用操作流程，供用例与套件引用', adminOnly: false, sidebarOnly: true },
      { name: 'global-params', label: '全局参数', hint: '平台 / 项目 / 团队三级变量统一注入执行上下文', adminOnly: true, sidebarOnly: true },
      { name: 'assert-policy', label: '断言策略', hint: '配置断言白名单与校验规则策略', adminOnly: true }
    ]
  },
  {
    id: 'runtime',
    title: '录制与调度执行',
    tip: '录屏质量、动态造数与测试账号池；定时任务请从左侧「测试任务」进入',
    tabs: [
      { name: 'recording', label: '录屏配置', hint: '调整录制开关与识别率 / 定位命中阈值', adminOnly: false },
      { name: 'schedule', label: '定时调度', hint: '按 Cron 自动执行测试套件回归', adminOnly: false, sidebarOnly: true },
      { name: 'data-factory', label: '动态造数', hint: '接口造数模板，执行前自动准备业务数据', adminOnly: true },
      { name: 'accounts', label: '账号池', hint: '统一管理测试账号，供登录步骤引用', adminOnly: false }
    ]
  },
  {
    id: 'version',
    title: '版本与团队管理',
    tip: '团队空间隔离与版本基线比对，支撑多团队协作与版本回归',
    tabs: [
      { name: 'teams', label: '团队空间', hint: '创建团队并分配用户，资源按团队隔离', adminOnly: true },
      { name: 'baseline', label: '版本基线', hint: '记录 APP / 套件 / 环境基线，支持版本比对', adminOnly: false }
    ]
  },
  {
    id: 'ops',
    title: '安全、备份与运维',
    tip: '低频运维配置：凭据加密、灾备备份、健康监控、安全审计与回收站恢复',
    tabs: [
      { name: 'credentials', label: '加密凭据', hint: '敏感密钥 AES 加密存储与按需解密', adminOnly: true },
      { name: 'backup', label: '灾备备份', hint: '一键备份与还原用例 / 步骤 / 环境等核心数据', adminOnly: true },
      { name: 'monitor', label: '健康监控', hint: '查看数据库、执行器、存储与设备池健康状态', adminOnly: true },
      { name: 'audit', label: '安全审计', hint: '追踪关键操作日志，满足安全合规审计', adminOnly: true },
      { name: 'recycle', label: '回收站', hint: '恢复误删的用例、套件、公共步骤等资源', adminOnly: false }
    ]
  }
]

const activeGroupId = ref('reuse')
const groupTabsCollapsed = ref(false)

const visibleNavGroups = computed(() => NAV_GROUPS.map(g => ({
  ...g,
  tabs: g.tabs.filter(t => !t.sidebarOnly && (!t.adminOnly || userStore.isAdmin))
})).filter(g => g.tabs.length > 0))

const activeGroupTabs = computed(() => {
  const g = visibleNavGroups.value.find(x => x.id === activeGroupId.value)
  return g?.tabs || []
})

function findGroupIdByTab(tabName) {
  const g = NAV_GROUPS.find(x => x.tabs.some(t => t.name === tabName))
  return g?.id || visibleNavGroups.value[0]?.id || 'reuse'
}

function isSidebarOnlyTab(tabName) {
  return NAV_GROUPS.some(g => g.tabs.some(t => t.name === tabName && t.sidebarOnly))
}

function firstVisibleTab() {
  return visibleNavGroups.value[0]?.tabs[0]?.name || 'dataset'
}

function toggleGroup(id) {
  if (activeGroupId.value === id) {
    groupTabsCollapsed.value = !groupTabsCollapsed.value
    return
  }
  activeGroupId.value = id
  groupTabsCollapsed.value = false
  const tabs = visibleNavGroups.value.find(g => g.id === id)?.tabs || []
  if (tabs.length && !tabs.some(t => t.name === activeTab.value)) {
    selectTab(tabs[0].name)
  }
}

function selectTab(name) {
  activeTab.value = name
  activeGroupId.value = findGroupIdByTab(name)
  groupTabsCollapsed.value = isSidebarOnlyTab(name)
  syncTabToRoute(name)
}

function onTabChange(name) {
  activeGroupId.value = findGroupIdByTab(name)
  groupTabsCollapsed.value = isSidebarOnlyTab(name)
  syncTabToRoute(name)
}

function syncTabToRoute(name) {
  if (soloTab.value) return
  if (String(route.query.tab || '') === name) return
  router.replace({ query: { ...route.query, tab: name } })
}

function applyTabFromRoute() {
  // 独立入口：强制锁定到对应模块
  if (soloTab.value) {
    if (soloTab.value === 'global-params' && !userStore.isAdmin) {
      router.replace('/platform-config')
      return
    }
    activeTab.value = soloTab.value
    activeGroupId.value = findGroupIdByTab(soloTab.value)
    groupTabsCollapsed.value = true
    return
  }

  const tab = String(route.query.tab || '')
  // 旧链接兼容：平台配置内的公共步骤 / 全局参数 → 独立路由
  if (tab === 'steps') {
    router.replace('/common-steps')
    return
  }
  if (tab === 'global-params') {
    router.replace(userStore.isAdmin ? '/global-params' : '/platform-config')
    return
  }
  if (tab && TAB_ALLOW.has(tab)) {
    if (['teams', 'assert-policy', 'data-factory', 'credentials', 'backup', 'monitor', 'audit'].includes(tab)
      && !userStore.isAdmin) {
      activeTab.value = firstVisibleTab()
      activeGroupId.value = findGroupIdByTab(activeTab.value)
      groupTabsCollapsed.value = false
      return
    }
    activeTab.value = tab
    activeGroupId.value = findGroupIdByTab(tab)
    // 侧栏直达的高频页：收起分组子导航，避免与侧栏入口重复抢视线
    groupTabsCollapsed.value = isSidebarOnlyTab(tab)
    return
  }
  if (!tab) {
    activeTab.value = firstVisibleTab()
    activeGroupId.value = findGroupIdByTab(activeTab.value)
    groupTabsCollapsed.value = false
  }
}

watch(() => route.query.tab, () => applyTabFromRoute())
watch(soloTab, () => applyTabFromRoute())
const envs = ref([])
const datasets = ref([])
const commonSteps = ref([])
const schedules = ref([])
const baselines = ref([])
const teams = ref([])
const showTeamDialog = ref(false)
const editingTeamRow = ref(null)
const credentials = ref([])
const backups = ref([])
const backupCreating = ref(false)
const seedInstalling = ref(false)
const monitor = ref({})
const monitorLoading = ref(false)
const executorEvents = ref([])
const auditLogs = ref([])
const auditLoading = ref(false)
const auditPage = ref(1)
const auditTotal = ref(0)
const auditStats = ref({ total: 0, evidence_count: 0, backup_count: 0 })
const accounts = ref([])
const recycleItems = ref([])
const recycleStats = ref({ total: 0, common_step: 0, case_suite: 0, data_set: 0, expired: 0 })
const selectedStepIds = ref([])
const selectedStepRows = ref([])
const stepsLoading = ref(false)
const stepPage = ref(1)
const stepPageSize = ref(15)
const stepFilters = reactive({ keyword: '', status: '', category: '', platform: '' })
const showStepTemplates = ref(false)
const userOptions = ref([])
const showTransferDialog = ref(false)
const showTutorialDialog = ref(false)
const transferOwnerId = ref(null)
const suiteOptions = ref([])
const recordingFeatures = ref({
  recording_v2: true,
  min_recognition_rate: 95,
  min_locator_hit_rate: 98,
  max_long_tasks_per_min: 2,
  runtime_overrides: false,
  source: 'yaml_default'
})
const recordingForm = reactive({
  recording_v2: true,
  min_recognition_rate: 95,
  min_locator_hit_rate: 98,
  max_long_tasks_per_min: 2
})
const recordingFeaturesLoading = ref(false)
const recordingFeaturesSaving = ref(false)

const showEnvDialog = ref(false)
const editingEnvRow = ref(null)
const showDatasetDialog = ref(false)
const editingDatasetRow = ref(null)
const showStepDialog = ref(false)
const editingStepRow = ref(null)

function stepCategoryLabel(row) {
  const meta = extractMetaFromRow(row)
  return categoryLabel(meta.category) || '—'
}
function stepPlatformLabel(row) {
  const meta = extractMetaFromRow(row)
  return platformLabel(meta.platform) || '—'
}
const showScheduleDialog = ref(false)
const editingScheduleRow = ref(null)
const showAccountDialog = ref(false)
const editingAccountRow = ref(null)
const showCommentDialog = ref(false)
const showStepViewDialog = ref(false)
const viewingStepRow = ref(null)
const commentAssetType = ref('common_step')
const commentAssetId = ref(null)
const commentAssetName = ref('')
const comments = ref([])
const newComment = ref('')
const showBaselineDialog = ref(false)
const editingBaselineRow = ref(null)
const showBaselineCompareDialog = ref(false)
const baselineCompareResult = ref(null)
const showCredentialDialog = ref(false)
const editingCredentialRow = ref(null)
const globalParams = ref([])
const showGlobalParamDialog = ref(false)
const editingGlobalParamRow = ref(null)
const showGlobalParamLogDialog = ref(false)
const globalParamLogs = ref([])
const assertPolicies = ref([])
const showAssertPolicyDialog = ref(false)
const editingAssertPolicyRow = ref(null)
const dataFactoryTemplates = ref([])
const showDataFactoryDialog = ref(false)
const editingDataFactoryRow = ref(null)

const hasStepSelection = computed(() => selectedStepIds.value.length > 0)

function isTemplateStep(row) {
  const text = `${row.name || ''}${row.description || ''}`
  return /模板|template/i.test(text)
}

const filteredSteps = computed(() => {
  const kw = (stepFilters.keyword || '').trim().toLowerCase()
  return (commonSteps.value || []).filter(row => {
    if (stepFilters.status && row.status !== stepFilters.status) return false
    const meta = extractMetaFromRow(row)
    if (stepFilters.category && meta.category !== stepFilters.category) return false
    if (stepFilters.platform && meta.platform !== stepFilters.platform) return false
    if (!kw) return true
    return String(row.name || '').toLowerCase().includes(kw)
      || String(row.description || '').toLowerCase().includes(kw)
  })
})

const pagedSteps = computed(() => {
  const start = (stepPage.value - 1) * stepPageSize.value
  return filteredSteps.value.slice(start, start + stepPageSize.value)
})

watch(filteredSteps, (list) => {
  const maxPage = Math.max(1, Math.ceil(list.length / stepPageSize.value) || 1)
  if (stepPage.value > maxPage) stepPage.value = maxPage
})

const stepStats = computed(() => {
  const list = commonSteps.value || []
  return {
    all: list.length,
    active: list.filter(s => s.status === 'active').length,
    deprecated: list.filter(s => s.status === 'deprecated').length,
    template: list.filter(isTemplateStep).length
  }
})

const moduleStats = computed(() => {
  const tab = activeTab.value
  if (tab === 'steps') {
    return [
      { key: 'all', label: '全部公共步骤', value: `${stepStats.value.all} 套`, tone: 'tone-all', onClick: () => { stepFilters.status = ''; stepPage.value = 1 } },
      { key: 'active', label: '已启用可用步骤', value: `${stepStats.value.active} 套`, tone: 'tone-ok', valueClass: 'is-ok', onClick: () => { stepFilters.status = 'active'; stepPage.value = 1 } },
      { key: 'off', label: '已停用步骤', value: `${stepStats.value.deprecated} 套`, tone: 'tone-muted', valueClass: 'is-muted', onClick: () => { stepFilters.status = 'deprecated'; stepPage.value = 1 } },
      { key: 'tpl', label: '模板公共步骤', value: `${stepStats.value.template} 套`, tone: 'tone-tpl', onClick: () => { stepFilters.keyword = '模板'; stepPage.value = 1 } }
    ]
  }
  if (tab === 'env') {
    const list = envs.value || []
    return [
      { key: 'e1', label: '全部环境', value: list.length, tone: 'tone-all' },
      { key: 'e2', label: '测试环境', value: list.filter(e => e.env_type === 'test').length, tone: 'tone-ok', valueClass: 'is-ok' },
      { key: 'e3', label: '预发灰度', value: list.filter(e => ['staging', 'gray'].includes(e.env_type)).length, tone: 'tone-tpl' },
      { key: 'e4', label: '生产正式', value: list.filter(e => e.env_type === 'prod').length, tone: 'tone-muted', valueClass: 'is-muted' }
    ]
  }
  if (tab === 'dataset') {
    return [
      { key: 'd1', label: '全部数据集', value: datasets.value.length, tone: 'tone-all' },
      { key: 'd2', label: '可编辑数据集', value: datasets.value.length, tone: 'tone-ok', valueClass: 'is-ok' },
      { key: 'd3', label: '环境配置数', value: envs.value.length, tone: 'tone-tpl' },
      { key: 'd4', label: '公共步骤数', value: commonSteps.value.length, tone: 'tone-muted' }
    ]
  }
  if (tab === 'schedule') {
    const list = schedules.value || []
    return [
      { key: 's1', label: '全部定时任务', value: list.length, tone: 'tone-all' },
      { key: 's2', label: '已启用任务', value: list.filter(s => s.enabled).length, tone: 'tone-ok', valueClass: 'is-ok' },
      { key: 's3', label: '已停用任务', value: list.filter(s => !s.enabled).length, tone: 'tone-muted', valueClass: 'is-muted' },
      { key: 's4', label: '关联套件数', value: new Set(list.map(s => s.suite_id).filter(Boolean)).size, tone: 'tone-tpl' }
    ]
  }
  if (tab === 'accounts') {
    const list = accounts.value || []
    return [
      { key: 'a1', label: '全部账号', value: list.length, tone: 'tone-all' },
      { key: 'a2', label: '可用账号', value: list.filter(a => a.status !== 'archived').length, tone: 'tone-ok', valueClass: 'is-ok' },
      { key: 'a3', label: '已归档', value: list.filter(a => a.status === 'archived').length, tone: 'tone-muted', valueClass: 'is-muted' },
      { key: 'a4', label: '回收站条目', value: recycleItems.value.length, tone: 'tone-tpl' }
    ]
  }
  if (tab === 'recycle') {
    const s = recycleStats.value || {}
    return [
      { key: 'r1', label: '回收站总数', value: s.total ?? recycleItems.value.length, tone: 'tone-all' },
      { key: 'r2', label: '公共步骤', value: s.common_step ?? 0, tone: 'tone-ok' },
      { key: 'r3', label: '用例 / 套件', value: s.case_suite ?? 0, tone: 'tone-tpl' },
      { key: 'r4', label: '数据集', value: s.data_set ?? 0, tone: 'tone-muted' }
    ]
  }
  if (tab === 'global-params') {
    const list = globalParams.value || []
    return [
      { key: 'g1', label: '全部参数', value: list.length, tone: 'tone-all' },
      { key: 'g2', label: '平台全局', value: list.filter(p => p.scope === 'platform').length, tone: 'tone-ok' },
      { key: 'g3', label: '项目 / 团队', value: list.filter(p => ['project', 'team', 'env'].includes(p.scope)).length, tone: 'tone-tpl' },
      { key: 'g4', label: '敏感参数', value: list.filter(p => p.sensitive).length, tone: 'tone-muted' }
    ]
  }
  if (tab === 'baseline') {
    return [
      { key: 'b1', label: '版本基线', value: baselines.value.length, tone: 'tone-all' },
      { key: 'b2', label: '环境数', value: envs.value.length, tone: 'tone-ok' },
      { key: 'b3', label: '定时任务', value: schedules.value.length, tone: 'tone-tpl' },
      { key: 'b4', label: '团队数', value: teams.value.length, tone: 'tone-muted' }
    ]
  }
  if (tab === 'teams') {
    return [
      { key: 't1', label: '团队空间', value: teams.value.length, tone: 'tone-all' },
      { key: 't2', label: '平台用户', value: userOptions.value.length, tone: 'tone-ok' },
      { key: 't3', label: '已分配团队', value: userOptions.value.filter(u => u.team_id).length, tone: 'tone-tpl' },
      { key: 't4', label: '未分配', value: userOptions.value.filter(u => !u.team_id).length, tone: 'tone-muted', valueClass: 'is-muted' }
    ]
  }
  if (tab === 'recording') {
    return [
      { key: 'rc1', label: '录屏开关', value: recordingForm.recording_v2 ? '开' : '关', tone: 'tone-all' },
      { key: 'rc2', label: '识别率阈值', value: `${recordingForm.min_recognition_rate}%`, tone: 'tone-ok', valueClass: 'is-ok' },
      { key: 'rc3', label: '定位命中阈值', value: `${recordingForm.min_locator_hit_rate}%`, tone: 'tone-tpl' },
      { key: 'rc4', label: '配置来源', value: recordingFeatures.value.source === 'runtime' ? '运行时' : 'YAML', tone: 'tone-muted' }
    ]
  }
  if (tab === 'credentials') {
    return [
      { key: 'c1', label: '加密凭据', value: credentials.value.length, tone: 'tone-all' },
      { key: 'c2', label: '备份文件', value: backups.value.length, tone: 'tone-ok' },
      { key: 'c3', label: '审计日志', value: auditTotal.value, tone: 'tone-tpl' },
      { key: 'c4', label: '回收站', value: recycleItems.value.length, tone: 'tone-muted' }
    ]
  }
  if (tab === 'backup') {
    return [
      { key: 'bk1', label: '备份文件', value: backups.value.length, tone: 'tone-all' },
      { key: 'bk2', label: '公共步骤', value: commonSteps.value.length, tone: 'tone-ok' },
      { key: 'bk3', label: '环境数', value: envs.value.length, tone: 'tone-tpl' },
      { key: 'bk4', label: '数据集', value: datasets.value.length, tone: 'tone-muted' }
    ]
  }
  if (tab === 'monitor') {
    return [
      { key: 'm1', label: '整体状态', value: monitorOverallLabel.value, tone: monitor.value.overall === 'healthy' || !monitor.value.overall ? 'tone-ok' : 'tone-all', valueClass: monitor.value.overall === 'healthy' || !monitor.value.overall ? 'is-ok' : '' },
      { key: 'm2', label: '在线设备', value: monitor.value.devices?.online ?? '-', tone: 'tone-ok', valueClass: 'is-ok' },
      { key: 'm3', label: '运行任务', value: monitor.value.scheduler?.running_tasks ?? '-', tone: 'tone-tpl' },
      { key: 'm4', label: '故障事件', value: executorEvents.value.length, tone: executorEvents.value.length > 0 ? 'tone-all' : 'tone-muted', valueClass: executorEvents.value.length > 0 ? '' : 'is-muted' }
    ]
  }
  if (tab === 'audit') {
    return [
      { key: 'au1', label: '审计日志', value: auditTotal.value, tone: 'tone-all' },
      { key: 'au2', label: '当前页条数', value: auditLogs.value.length, tone: 'tone-ok', valueClass: 'is-ok' },
      { key: 'au3', label: '取证凭证', value: auditStats.value.evidence_count ?? 0, tone: (auditStats.value.evidence_count || 0) > 0 ? 'tone-tpl' : 'tone-muted', valueClass: (auditStats.value.evidence_count || 0) > 0 ? '' : 'is-muted' },
      { key: 'au4', label: '备份份数', value: auditStats.value.backup_count ?? backups.value.length, tone: 'tone-muted' }
    ]
  }
  if (tab === 'assert-policy') {
    const list = assertPolicies.value || []
    return [
      { key: 'ap1', label: '断言规则', value: list.length, tone: 'tone-all' },
      { key: 'ap2', label: '已启用', value: list.filter(p => p.enabled).length, tone: 'tone-ok', valueClass: 'is-ok' },
      { key: 'ap3', label: '已停用', value: list.filter(p => !p.enabled).length, tone: 'tone-muted', valueClass: 'is-muted' },
      { key: 'ap4', label: '全局参数', value: globalParams.value.length, tone: 'tone-tpl' }
    ]
  }
  if (tab === 'data-factory') {
    const list = dataFactoryTemplates.value || []
    return [
      { key: 'df1', label: '造数模板', value: list.length, tone: 'tone-all' },
      { key: 'df2', label: '已启用', value: list.filter(t => t.enabled).length, tone: 'tone-ok', valueClass: 'is-ok' },
      { key: 'df3', label: '已停用', value: list.filter(t => !t.enabled).length, tone: 'tone-muted', valueClass: 'is-muted' },
      { key: 'df4', label: '账号池', value: accounts.value.length, tone: 'tone-tpl' }
    ]
  }
  return [
    { key: 'x1', label: '环境', value: envs.value.length, tone: 'tone-all' },
    { key: 'x2', label: '数据集', value: datasets.value.length, tone: 'tone-ok' },
    { key: 'x3', label: '公共步骤', value: commonSteps.value.length, tone: 'tone-tpl' },
    { key: 'x4', label: '定时任务', value: schedules.value.length, tone: 'tone-muted' }
  ]
})

async function loadEnvs() { envs.value = (await envApi.list()).data }
async function loadDatasets() { datasets.value = (await datasetApi.list()).data }
async function loadSteps() {
  stepsLoading.value = true
  try {
    commonSteps.value = (await commonStepApi.list()).data || []
  } finally {
    stepsLoading.value = false
  }
}
async function refreshSteps() {
  await loadSteps()
  ElMessage.success('列表已刷新')
}
function resetStepFilters() {
  stepFilters.keyword = ''
  stepFilters.status = ''
  stepFilters.category = ''
  stepFilters.platform = ''
  stepPage.value = 1
}
function onStepSelectionChange(rows) {
  selectedStepRows.value = rows
  selectedStepIds.value = rows.map(r => r.id)
}
async function loadSchedules() { schedules.value = (await scheduleApi.list()).data }

function scheduleSuiteLabel(suiteId) {
  const s = (suiteOptions.value || []).find(x => x.id === suiteId)
  return s ? `${s.name}（ID: ${s.id}）` : (suiteId != null ? `套件 #${suiteId}` : '-')
}
async function loadBaselines() {
  try { baselines.value = (await baselineApi.list()).data } catch { baselines.value = [] }
}
async function loadTeams() {
  if (!userStore.isAdmin) return
  try {
    teams.value = (await teamApi.list()).data
    await loadUsers()
  } catch { teams.value = [] }
}
async function loadCredentials() {
  if (!userStore.isAdmin) return
  try { credentials.value = (await credentialApi.list()).data } catch { credentials.value = [] }
}
async function loadGlobalParams() {
  if (!userStore.isAdmin) return
  try { globalParams.value = (await globalParamApi.list()).data } catch { globalParams.value = [] }
}

function scopeLabel(scope) {
  return {
    platform: '平台全局',
    project: '项目专属',
    team: '团队私有',
    env: '项目专属'
  }[scope] || scope || '-'
}
async function loadAssertPolicies() {
  if (!userStore.isAdmin) return
  try { assertPolicies.value = (await assertPolicyApi.list()).data } catch { assertPolicies.value = [] }
}

function assertTargetLabel(t) {
  return {
    assert_type: '断言类型',
    element_name: '控件标识',
    toast_pattern: '断言类型'
  }[t] || t || '-'
}
async function loadDataFactoryTemplates() {
  if (!userStore.isAdmin) return
  try { dataFactoryTemplates.value = (await dataFactoryApi.listTemplates()).data } catch { dataFactoryTemplates.value = [] }
}

function methodLabel(m) {
  return {
    POST: 'POST 新增',
    GET: 'GET 查询',
    PUT: 'PUT 修改',
    DELETE: 'DELETE 删除'
  }[m] || m || '-'
}
async function loadBackups() {
  if (!userStore.isAdmin) return
  try { backups.value = (await backupApi.list()).data } catch { backups.value = [] }
}

const monitorOverallLabel = computed(() => {
  const o = monitor.value.overall
  if (o === 'healthy') return '运行正常'
  if (o === 'degraded' || o === 'critical') return '存在组件故障'
  return monitor.value.backend ? '运行正常' : '未知'
})

function onMonitorUpdated({ monitor: m, events }) {
  monitor.value = m || {}
  executorEvents.value = events || []
  monitorLoading.value = false
}

async function loadMonitor() {
  if (!userStore.isAdmin) return
  monitorLoading.value = true
  try {
    monitor.value = (await monitorApi.snapshot()).data
    executorEvents.value = (await monitorApi.executorEvents()).data || []
  } catch {
    monitor.value = {}
    executorEvents.value = []
  } finally {
    monitorLoading.value = false
  }
}
function onAuditUpdated({ logs, total, stats }) {
  auditLogs.value = logs || []
  auditTotal.value = total ?? 0
  if (stats) auditStats.value = stats
  auditLoading.value = false
}

async function loadAuditLogs() {
  if (!userStore.isAdmin) return
  auditLoading.value = true
  try {
    const [listRes, statsRes] = await Promise.all([
      auditApi.list({ page: auditPage.value, page_size: 20 }),
      auditApi.stats().catch(() => ({ data: null }))
    ])
    auditLogs.value = listRes.data?.list || []
    auditTotal.value = listRes.data?.total || 0
    if (statsRes?.data) auditStats.value = statsRes.data
  } catch {
    auditLogs.value = []
    auditTotal.value = 0
  } finally {
    auditLoading.value = false
  }
}
async function loadAccounts() { accounts.value = (await accountApi.list()).data }
async function loadRecycle() {
  try {
    const [listRes, statsRes] = await Promise.all([
      recycleApi.list(),
      recycleApi.stats().catch(() => ({ data: null }))
    ])
    recycleItems.value = listRes.data || []
    if (statsRes?.data) recycleStats.value = statsRes.data
  } catch {
    recycleItems.value = []
  }
}

function onRecycleUpdated({ items, stats }) {
  recycleItems.value = items || []
  if (stats) recycleStats.value = stats
}

async function loadUsers() {
  if (userStore.isAdmin) {
    userOptions.value = (await authApi.listUsers()).data
  }
}

function openStepTransfer() {
  transferOwnerId.value = null
  showTransferDialog.value = true
}

async function confirmStepTransfer() {
  if (!transferOwnerId.value) { ElMessage.warning('请选择负责人'); return }
  await commonStepApi.transfer(selectedStepIds.value, transferOwnerId.value)
  ElMessage.success('公共步骤已移交')
  showTransferDialog.value = false
  selectedStepIds.value = []
  loadSteps()
}

async function loadSuiteOptions() { suiteOptions.value = (await suiteApi.list()).data }

function envTypeLabel(t) {
  return { test: '测试环境', staging: '预发灰度环境', gray: '预发灰度环境', prod: '生产正式环境' }[t] || t || '—'
}

function openEnv(row) {
  editingEnvRow.value = row || null
  showEnvDialog.value = true
}

function openDataset(row) {
  editingDatasetRow.value = row || null
  showDatasetDialog.value = true
}

async function deleteDataset(row) {
  await ElMessageBox.confirm('确定删除该数据集？', '确认', { type: 'warning' })
  await datasetApi.delete(row.id)
  ElMessage.success('已删除')
  loadDatasets()
}

function openStep(row) {
  if (row?.id) {
    router.push(`/common-steps/${row.id}/edit`)
    return
  }
  router.push('/common-steps/new')
}

function viewStepDetail(row) {
  viewingStepRow.value = row || null
  showStepViewDialog.value = true
}

function createFromTemplate(templateId) {
  showStepTemplates.value = false
  router.push({ path: '/common-steps/new', query: { template: templateId } })
}

async function deleteStep(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除公共步骤「${row.name}」？删除后将移入回收站，可在回收站恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '移入回收站', cancelButtonText: '取消' }
    )
    await commonStepApi.delete(row.id)
    ElMessage.success('已移入回收站')
    loadSteps()
    loadRecycle()
  } catch (e) {
    if (e !== 'cancel' && e?.toString?.() !== 'cancel') { /* interceptor may show dependency error */ }
  }
}

async function copyStep(row) {
  const name = `${row.name}_副本`
  await commonStepApi.create({
    name,
    description: row.description || '',
    steps_content: row.steps_content || '{"steps":[]}',
    status: 'active'
  })
  ElMessage.success(`已复制为「${name}」`)
  loadSteps()
}

async function toggleStepStatus(row) {
  const next = row.status === 'active' ? 'deprecated' : 'active'
  await commonStepApi.update(row.id, {
    name: row.name,
    description: row.description,
    steps_content: row.steps_content,
    status: next
  })
  ElMessage.success(next === 'active' ? '已启用' : '已停用')
  loadSteps()
}

async function batchSetStepStatus(status) {
  if (!selectedStepRows.value.length) return
  const label = status === 'active' ? '启用' : '停用'
  await ElMessageBox.confirm(`确定批量${label}选中的 ${selectedStepRows.value.length} 条公共步骤？`, '批量操作', { type: 'warning' })
  for (const row of selectedStepRows.value) {
    await commonStepApi.update(row.id, {
      name: row.name,
      description: row.description,
      steps_content: row.steps_content,
      status
    })
  }
  ElMessage.success(`已批量${label}`)
  selectedStepIds.value = []
  selectedStepRows.value = []
  loadSteps()
}

async function batchCopySteps() {
  if (!selectedStepRows.value.length) return
  await ElMessageBox.confirm(`确定复制选中的 ${selectedStepRows.value.length} 条公共步骤？`, '批量复制', { type: 'info' })
  for (const row of selectedStepRows.value) {
    await commonStepApi.create({
      name: `${row.name}_副本`,
      description: row.description || '',
      steps_content: row.steps_content || '{"steps":[]}',
      status: 'active'
    })
  }
  ElMessage.success('批量复制完成')
  loadSteps()
}

async function batchDeleteSteps() {
  if (!selectedStepRows.value.length) return
  await ElMessageBox.confirm(
    `确定删除选中的 ${selectedStepRows.value.length} 条？删除后将移入回收站，可在回收站恢复。`,
    '批量删除',
    { type: 'warning', confirmButtonText: '移入回收站' }
  )
  for (const row of selectedStepRows.value) {
    try { await commonStepApi.delete(row.id) } catch { /* skip dependency failures */ }
  }
  ElMessage.success('已批量移入回收站')
  selectedStepIds.value = []
  selectedStepRows.value = []
  loadSteps()
  loadRecycle()
}

const STEP_TEMPLATES = [
  {
    name: '模板_通用登录',
    description: '行业标准登录模板：打开应用并完成账号密码登录',
    steps_content: JSON.stringify({
      steps: [
        { type: 'launch_app', name: '启动应用' },
        { type: 'input', name: '输入账号', locator_type: 'id', locator_value: 'username' },
        { type: 'input', name: '输入密码', locator_type: 'id', locator_value: 'password' },
        { type: 'click', name: '点击登录', locator_type: 'id', locator_value: 'login_btn' }
      ]
    }, null, 2)
  },
  {
    name: '模板_清理缓存',
    description: '行业标准清理模板：清理应用缓存并回到首页',
    steps_content: JSON.stringify({
      steps: [
        { type: 'shell', name: '清理应用缓存', command: 'pm clear ${app_package}' },
        { type: 'launch_app', name: '重新启动应用' }
      ]
    }, null, 2)
  },
  {
    name: '模板_应用初始化',
    description: '行业标准初始化模板：授权弹窗处理与首页就绪等待',
    steps_content: JSON.stringify({
      steps: [
        { type: 'launch_app', name: '启动应用' },
        { type: 'click', name: '同意隐私协议', locator_type: 'text', locator_value: '同意', optional: true },
        { type: 'wait', name: '等待首页加载', timeout: 5000 }
      ]
    }, null, 2)
  }
]

async function importStepTemplates() {
  if (!userStore.isAdmin) return
  await ElMessageBox.confirm(
    '将导入「通用登录 / 清理缓存 / 应用初始化」等行业标准步骤模板（名称含「模板」前缀）。已存在同名步骤将跳过。',
    '导入步骤模板',
    { type: 'info', confirmButtonText: '开始导入' }
  )
  const existing = new Set((commonSteps.value || []).map(s => s.name))
  let created = 0
  for (const tpl of STEP_TEMPLATES) {
    if (existing.has(tpl.name)) continue
    await commonStepApi.create({ ...tpl, status: 'active' })
    created += 1
  }
  ElMessage.success(created ? `已导入 ${created} 套模板步骤` : '模板均已存在，无需重复导入')
  loadSteps()
}

function showStepTutorial() {
  showTutorialDialog.value = true
}

async function showStepComments(row) {
  commentAssetType.value = 'common_step'
  commentAssetId.value = row.id
  commentAssetName.value = row.name
  newComment.value = ''
  comments.value = (await commentApi.list('common_step', row.id)).data
  showCommentDialog.value = true
}

async function submitComment() {
  if (!newComment.value.trim()) return
  await commentApi.create({ asset_type: commentAssetType.value, asset_id: commentAssetId.value, content: newComment.value.trim() })
  newComment.value = ''
  comments.value = (await commentApi.list(commentAssetType.value, commentAssetId.value)).data
  ElMessage.success('批注已发表')
}

async function deleteComment(c) {
  await ElMessageBox.confirm('确定删除该批注？', '确认', { type: 'warning' })
  await commentApi.delete(c.id)
  comments.value = (await commentApi.list(commentAssetType.value, commentAssetId.value)).data
}

function openBaseline(row) {
  editingBaselineRow.value = row || null
  showBaselineDialog.value = true
}

async function archiveBaseline(row) {
  await ElMessageBox.confirm(`归档基线「${row.name}」？`, '确认', { type: 'warning' })
  await baselineApi.archive(row.id)
  ElMessage.success('已归档')
  loadBaselines()
}

async function compareBaseline(row) {
  const res = await baselineApi.compare(row.id)
  baselineCompareResult.value = res.data
  showBaselineCompareDialog.value = true
}

function openTeam(row) {
  editingTeamRow.value = row || null
  showTeamDialog.value = true
}

function onTeamSaved() {
  loadTeams()
  loadUsers()
}

async function assignUserTeam(userId, teamId) {
  await teamApi.assignUser(userId, teamId)
  ElMessage.success('用户团队已更新')
  loadUsers()
}

function openCredential(row) {
  editingCredentialRow.value = row || null
  showCredentialDialog.value = true
}

function credentialCategoryLabel(c) {
  if (!c) return '—'
  const map = {
    api: '接口鉴权', auth: '接口鉴权', db: '数据库密钥', database: '数据库密钥',
    account: '测试账号凭证', cert: '第三方证书', certificate: '第三方证书', other: '其他密钥'
  }
  return map[String(c).toLowerCase()] || c
}

function credentialScopeLabel(s) {
  return ({ platform: '平台全局', project: '项目专属', team: '团队私有' }[s] || '平台全局')
}

function credentialEnvLabel(id) {
  if (id == null || id === '') return '未绑定'
  const hit = (envs.value || []).find((e) => String(e.id) === String(id))
  return hit ? `${hit.name}（ID: ${hit.id}）` : `ID: ${id}`
}

async function deleteCredential(row) {
  await ElMessageBox.confirm(`删除凭据「${row.name}」？`, '确认', { type: 'warning' })
  await credentialApi.delete(row.id)
  loadCredentials()
}

function openGlobalParam(row) {
  editingGlobalParamRow.value = row || null
  showGlobalParamDialog.value = true
}

async function toggleGlobalParam(row, enabled) {
  await globalParamApi.toggle(row.id, enabled)
  loadGlobalParams()
}

async function showGlobalParamLogs(row) {
  globalParamLogs.value = (await globalParamApi.logs(row.id)).data
  showGlobalParamLogDialog.value = true
}

function openAssertPolicy(row) {
  editingAssertPolicyRow.value = row || null
  showAssertPolicyDialog.value = true
}

async function deleteAssertPolicy(row) {
  await ElMessageBox.confirm('确定删除该规则？', '确认', { type: 'warning' })
  await assertPolicyApi.delete(row.id)
  loadAssertPolicies()
}

function openDataFactory(row) {
  editingDataFactoryRow.value = row || null
  showDataFactoryDialog.value = true
}

async function createBackup() {
  backupCreating.value = true
  try {
    await backupApi.create()
    ElMessage.success('备份已创建')
    loadBackups()
  } finally {
    backupCreating.value = false
  }
}

async function installPortableSeed() {
  await ElMessageBox.confirm(
    '将按 ID 合并导入便携种子（控件池/用例/公共步骤/套件等）。同 ID 会被覆盖，是否继续？',
    '导入便携种子',
    { type: 'warning' }
  )
  seedInstalling.value = true
  try {
    const res = await backupApi.installPortableSeed()
    ElMessage.success(res.data?.message || `导入完成（${res.data?.count ?? 0} 条）`)
    loadBackups()
  } finally {
    seedInstalling.value = false
  }
}

async function restoreBackup(row) {
  await ElMessageBox.confirm(`还原备份「${row.filename}」？将合并覆盖用例/公共步骤/环境。`, '确认', { type: 'warning' })
  const res = await backupApi.restore(row.filename)
  ElMessage.success(res.data.message || '还原完成')
}

function downloadBackup(row) {
  window.open(backupApi.downloadUrl(row.filename), '_blank')
}

async function deleteBackup(row) {
  await ElMessageBox.confirm(`删除备份「${row.filename}」？`, '确认', { type: 'warning' })
  await backupApi.delete(row.filename)
  loadBackups()
}

function openSchedule(row) {
  editingScheduleRow.value = row || null
  showScheduleDialog.value = true
}

async function deleteSchedule(row) {
  await ElMessageBox.confirm('确定删除该定时任务？', '确认', { type: 'warning' })
  await scheduleApi.delete(row.id)
  ElMessage.success('已删除')
  loadSchedules()
}

function openAccount(row) {
  editingAccountRow.value = row || null
  showAccountDialog.value = true
}

function accountProjectLabel(key) {
  if (!key) return '—'
  if (key === 'default') return '默认业务项目'
  const teamHit = String(key).match(/^team-(\d+)$/)
  if (teamHit) {
    const t = (teams.value || []).find((x) => String(x.id) === teamHit[1])
    return t?.name || key
  }
  try {
    const list = JSON.parse(localStorage.getItem('atp_project_list') || '[]')
    const hit = (list || []).find((p) => p.id === key)
    if (hit?.name) return hit.name
  } catch { /* ignore */ }
  return key
}

function accountTeamLabel(id) {
  if (id == null || id === '') return '—'
  const hit = (teams.value || []).find((t) => String(t.id) === String(id))
  return hit?.name || `#${id}`
}

async function deleteAccount(row) {
  await ElMessageBox.confirm('确定删除该测试账号？', '确认', { type: 'warning' })
  await accountApi.delete(row.id)
  ElMessage.success('已删除')
  loadAccounts()
}

async function loadRecordingFeatures() {
  recordingFeaturesLoading.value = true
  try {
    const res = await recordingApi.features()
    recordingFeatures.value = { ...recordingFeatures.value, ...(res.data || {}) }
    Object.assign(recordingForm, {
      recording_v2: recordingFeatures.value.recording_v2 !== false,
      min_recognition_rate: recordingFeatures.value.min_recognition_rate ?? 95,
      min_locator_hit_rate: recordingFeatures.value.min_locator_hit_rate ?? 98,
      max_long_tasks_per_min: recordingFeatures.value.max_long_tasks_per_min ?? 2
    })
  } finally {
    recordingFeaturesLoading.value = false
  }
}

async function saveRecordingFeatures() {
  recordingFeaturesSaving.value = true
  try {
    const res = await recordingApi.updateFeatures({ ...recordingForm })
    recordingFeatures.value = { ...recordingFeatures.value, ...(res.data || {}) }
    invalidateRecordingFeatures()
    ElMessage.success('录屏配置已保存，全局立即生效')
  } finally {
    recordingFeaturesSaving.value = false
  }
}

async function resetRecordingFeatures() {
  await ElMessageBox.confirm('将清除运行时覆盖并恢复 application.yml 默认值，是否继续？', '确认', { type: 'warning' })
  recordingFeaturesSaving.value = true
  try {
    const res = await recordingApi.resetFeatures()
    recordingFeatures.value = { ...recordingFeatures.value, ...(res.data || {}) }
    Object.assign(recordingForm, {
      recording_v2: recordingFeatures.value.recording_v2 !== false,
      min_recognition_rate: recordingFeatures.value.min_recognition_rate ?? 95,
      min_locator_hit_rate: recordingFeatures.value.min_locator_hit_rate ?? 98,
      max_long_tasks_per_min: recordingFeatures.value.max_long_tasks_per_min ?? 2
    })
    invalidateRecordingFeatures()
    ElMessage.success('已恢复 YAML 默认配置')
  } finally {
    recordingFeaturesSaving.value = false
  }
}

onMounted(() => {
  applyTabFromRoute()
  loadEnvs(); loadDatasets(); loadSteps(); loadSchedules(); loadBaselines(); loadTeams()
  loadGlobalParams(); loadAssertPolicies(); loadDataFactoryTemplates(); loadCredentials()
  loadBackups(); loadMonitor(); loadAuditLogs(); loadAccounts(); loadRecycle()
  loadSuiteOptions(); loadUsers(); loadRecordingFeatures()
})
</script>

<style scoped>
.config-page :deep(.page-header__info h2) {
  font-size: 24px;
  font-weight: 700;
}
.core-tabs :deep(.el-tabs__header) { margin-bottom: 16px; }
.core-tabs--headless :deep(.el-tabs__header) { display: none; }
.core-tabs--headless :deep(.el-tabs__content) { padding: 0; }

.config-nav {
  margin-bottom: 16px;
  padding: 12px 14px 10px;
  background: linear-gradient(180deg, #ffffff 0%, #f1f5f9 100%);
  border: 1px solid #cbd5e1;
  border-radius: 14px;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.06);
}
.nav-groups {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}
.nav-group-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  border: 1.5px solid #cbd5e1;
  background: #fff;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  padding: 10px 12px;
  border-radius: 10px;
  line-height: 1.2;
  transition: color 0.15s, border-color 0.15s, background 0.15s, box-shadow 0.15s, transform 0.15s;
}
.nav-group-chip:hover {
  color: var(--atp-primary);
  border-color: rgba(37, 99, 235, 0.45);
  background: #f8fbff;
  transform: translateY(-1px);
}
.nav-group-chip.active {
  color: #fff;
  border-color: var(--atp-primary);
  background: var(--atp-primary);
  font-weight: 700;
  box-shadow: 0 6px 16px rgba(37, 99, 235, 0.28);
}
.nav-group-label {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.nav-fold-icon { font-size: 12px; flex-shrink: 0; }
.nav-count {
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: #e2e8f0;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.nav-group-chip.active .nav-count {
  background: rgba(255, 255, 255, 0.22);
  color: #fff;
}
.nav-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  padding: 10px;
  border-top: 1px solid #cbd5e1;
  background: #fff;
  border-radius: 10px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
}
.nav-tab {
  border: 1.5px solid #e2e8f0;
  background: #f8fafc;
  cursor: pointer;
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  border-radius: 8px;
  line-height: 1.2;
  transition: color 0.15s, border-color 0.15s, background 0.15s, box-shadow 0.15s, transform 0.15s;
}
.nav-tab:hover {
  color: var(--atp-primary);
  border-color: rgba(37, 99, 235, 0.4);
  background: #eff6ff;
  transform: translateY(-1px);
}
.nav-tab.active {
  color: #fff;
  font-weight: 700;
  border-color: var(--atp-primary);
  background: var(--atp-primary);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25);
}

@media (max-width: 960px) {
  .nav-groups { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 560px) {
  .nav-groups { grid-template-columns: 1fr; }
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.stat-card {
  padding: 16px 18px;
  border-radius: 12px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}
.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
}
.stat-card.tone-all { background: #eff6ff; }
.stat-card.tone-ok { background: #ecfdf5; }
.stat-card.tone-muted { background: #f1f5f9; }
.stat-card.tone-tpl { background: #fff7ed; }
.stat-value {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--atp-text);
}
.stat-value.is-ok { color: #059669; }
.stat-value.is-muted { color: #64748b; }
.stat-label {
  margin-top: 6px;
  font-size: 13px;
  color: var(--atp-text-secondary);
}

.toolbar-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.batch-wrap {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
}
.btn-muted { --el-button-bg-color: #f1f5f9; --el-button-border-color: #e2e8f0; }
.btn-muted-sm {
  --el-button-bg-color: #f8fafc;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #64748b;
}
.steps-filter { margin-bottom: 12px; }
.filter-right { margin-left: auto; display: flex; gap: 8px; flex-wrap: wrap; }
.row-actions { display: flex; flex-wrap: wrap; gap: 6px; }

.table-empty {
  text-align: center;
  padding: 36px 16px 20px;
}
.empty-title {
  margin: 0 0 14px;
  font-size: 15px;
  font-weight: 600;
  color: var(--atp-text);
}
.empty-actions { display: flex; justify-content: center; gap: 10px; flex-wrap: wrap; }
.empty-hint {
  margin: 12px auto 0;
  max-width: 420px;
  font-size: 12px;
  color: var(--atp-text-secondary);
  line-height: 1.5;
}
.pager-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--atp-border-neutral);
}
.pager-stats { font-size: 13px; color: var(--atp-text-secondary); }

.guide-bar {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 20px;
  margin-top: 20px;
  padding: 20px 22px;
  background: #f8fafc;
  border-radius: 14px;
  border: 1px solid var(--atp-border-neutral);
}
.guide-left h4,
.guide-right h4 {
  margin: 0 0 10px;
  font-size: 14px;
}
.guide-left ul {
  margin: 0;
  padding-left: 18px;
  font-size: 13px;
  color: var(--atp-text-secondary);
  line-height: 1.8;
}
.guide-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.guide-hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--atp-text-secondary);
  line-height: 1.5;
}

.tutorial-list {
  margin: 0;
  padding-left: 22px;
  color: var(--atp-text);
}
.tutorial-list > li {
  margin-bottom: 18px;
  padding-bottom: 14px;
  border-bottom: 1px dashed #e2e8f0;
  line-height: 1.6;
}
.tutorial-list > li:last-child {
  margin-bottom: 8px;
  padding-bottom: 0;
  border-bottom: none;
}
.tutorial-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--atp-text);
  margin-bottom: 8px;
}
.tutorial-list p {
  margin: 0 0 6px;
  font-size: 13px;
  color: #64748b;
  line-height: 1.65;
}
.tutorial-list p:last-child { margin-bottom: 0; }
.tutorial-tip {
  margin: 12px 0 0;
  padding: 10px 12px;
  font-size: 12px;
  color: #475569;
  line-height: 1.5;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.comment-list { max-height: 280px; overflow-y: auto; }
.tpl-grid { display: flex; flex-direction: column; gap: 8px; }
.tpl-card {
  text-align: left; border: 1px solid #e2e8f0; border-radius: 8px;
  padding: 10px 12px; background: #fff; cursor: pointer;
}
.tpl-card:hover { border-color: #93c5fd; background: #f8fafc; }
.tpl-card strong { display: block; margin-bottom: 4px; }
.tpl-card span { font-size: 12px; color: #64748b; }
.comment-empty { color: var(--atp-text-secondary); font-size: 13px; padding: 12px 0; text-align: center; }
.comment-item { padding: 10px 0; border-bottom: 1px solid var(--atp-border-neutral); }
.comment-meta { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--atp-text-secondary); margin-bottom: 4px; }
.comment-author { font-weight: 600; }
.comment-time { flex: 1; }
.comment-body { font-size: 14px; line-height: 1.5; white-space: pre-wrap; }
.tab-hint { margin-top: 10px; font-size: 12px; color: var(--atp-text-secondary); }
.form-hint { margin: 4px 0 0; font-size: 12px; color: var(--atp-text-secondary); line-height: 1.5; }

@media (max-width: 960px) {
  .stats-row { grid-template-columns: repeat(2, 1fr); }
  .guide-bar { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .stats-row { grid-template-columns: 1fr; }
  .filter-right { margin-left: 0; width: 100%; }
}
</style>
