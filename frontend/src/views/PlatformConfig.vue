<template>
  <div class="page-container config-page">
    <PageHeader title="平台配置" subtitle="环境、数据集、公共步骤、定时调度与回收站" />

    <el-tabs v-model="activeTab" class="core-tabs">
      <el-tab-pane label="环境配置" name="env">
        <AppCard :hover="false">
          <div style="margin-bottom:12px"><el-button type="primary" size="small" @click="openEnv()">添加环境</el-button></div>
          <el-table :data="envs" stripe size="small">
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="env_type" label="类型" width="100" />
            <el-table-column prop="base_url" label="Base URL" show-overflow-tooltip />
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button size="small" type="primary" plain @click="openEnv(row)">编辑</el-button>
                <el-button size="small" type="danger" plain @click="envApi.delete(row.id).then(loadEnvs)">禁用</el-button>
              </template>
            </el-table-column>
          </el-table>
        </AppCard>
      </el-tab-pane>

      <el-tab-pane label="数据集" name="dataset">
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

      <el-tab-pane label="公共步骤" name="steps">
        <AppCard :hover="false">
          <div style="margin-bottom:12px;display:flex;gap:8px">
            <el-button v-if="userStore.isAdmin" type="primary" size="small" @click="openStep()">添加步骤</el-button>
            <el-button v-if="userStore.isAdmin" size="small" :disabled="!selectedStepIds.length" @click="openStepTransfer">移交选中</el-button>
          </div>
          <el-table :data="commonSteps" stripe size="small" @selection-change="rows => selectedStepIds = rows.map(r => r.id)">
            <el-table-column v-if="userStore.isAdmin" type="selection" width="45" />
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="description" label="描述" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="90" />
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button v-if="userStore.isAdmin" size="small" type="primary" plain @click="openStep(row)">编辑</el-button>
                <el-button size="small" plain @click="showStepComments(row)">批注</el-button>
                <el-button v-if="userStore.isAdmin" size="small" type="danger" plain @click="deleteStep(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </AppCard>
      </el-tab-pane>

      <el-tab-pane label="录屏配置" name="recording">
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

      <el-tab-pane label="定时调度" name="schedule">
        <AppCard :hover="false">
          <div style="margin-bottom:12px"><el-button type="primary" size="small" @click="openSchedule()">添加定时任务</el-button></div>
          <el-table :data="schedules" stripe size="small">
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="suite_id" label="套件ID" width="90" />
            <el-table-column prop="cron_expression" label="Cron" width="140" />
            <el-table-column prop="enabled" label="状态" width="80">
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

      <el-tab-pane v-if="userStore.isAdmin" label="团队空间" name="teams">
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

      <el-tab-pane label="版本基线" name="baseline">
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

      <el-tab-pane v-if="userStore.isAdmin" label="全局参数" name="global-params">
        <AppCard :hover="false">
          <div style="margin-bottom:12px"><el-button type="primary" size="small" @click="openGlobalParam()">添加参数</el-button></div>
          <el-table :data="globalParams" stripe size="small">
            <el-table-column prop="param_key" label="参数键" width="160" />
            <el-table-column prop="scope" label="作用域" width="90">
              <template #default="{ row }">{{ row.scope === 'platform' ? '平台' : '环境' }}</template>
            </el-table-column>
            <el-table-column prop="env_id" label="环境ID" width="80" />
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
          <p class="tab-hint">执行时注入变量链（平台级优先于 YAML，环境级随 env_id 合并）；敏感参数 AES 加密存储</p>
        </AppCard>
      </el-tab-pane>

      <el-tab-pane v-if="userStore.isAdmin" label="断言策略" name="assert-policy">
        <AppCard :hover="false">
          <div style="margin-bottom:12px"><el-button type="primary" size="small" @click="openAssertPolicy()">添加规则</el-button></div>
          <el-table :data="assertPolicies" stripe size="small">
            <el-table-column prop="rule_type" label="类型" width="100">
              <template #default="{ row }">{{ row.rule_type === 'whitelist' ? '白名单(软断言)' : '黑名单' }}</template>
            </el-table-column>
            <el-table-column prop="target_type" label="目标" width="120" />
            <el-table-column prop="pattern" label="匹配模式" show-overflow-tooltip />
            <el-table-column prop="description" label="说明" show-overflow-tooltip />
            <el-table-column prop="enabled" label="启用" width="70">
              <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <el-button size="small" type="primary" plain @click="openAssertPolicy(row)">编辑</el-button>
                <el-button size="small" type="danger" plain @click="deleteAssertPolicy(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <p class="tab-hint">白名单：断言失败时跳过；黑名单：直接跳过该断言不执行</p>
        </AppCard>
      </el-tab-pane>

      <el-tab-pane v-if="userStore.isAdmin" label="动态造数" name="data-factory">
        <AppCard :hover="false">
          <div style="margin-bottom:12px"><el-button type="primary" size="small" @click="openDataFactory()">添加模板</el-button></div>
          <el-table :data="dataFactoryTemplates" stripe size="small">
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="method" label="方法" width="80" />
            <el-table-column prop="url_template" label="URL" show-overflow-tooltip />
            <el-table-column prop="enabled" label="启用" width="70">
              <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag></template>
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

      <el-tab-pane v-if="userStore.isAdmin" label="加密凭据" name="credentials">
        <AppCard :hover="false">
          <div style="margin-bottom:12px"><el-button type="primary" size="small" @click="openCredential()">添加凭据</el-button></div>
          <el-table :data="credentials" stripe size="small">
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="category" label="分类" width="100" />
            <el-table-column prop="value_masked" label="值" width="120" />
            <el-table-column prop="env_id" label="环境ID" width="80" />
            <el-table-column prop="description" label="描述" show-overflow-tooltip />
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <el-button size="small" type="primary" plain @click="openCredential(row)">编辑</el-button>
                <el-button size="small" type="danger" plain @click="deleteCredential(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <p class="tab-hint">执行时以 SECRET_名称 注入变量链，值 AES 加密存储</p>
        </AppCard>
      </el-tab-pane>

      <el-tab-pane v-if="userStore.isAdmin" label="灾备备份" name="backup">
        <AppCard :hover="false">
          <div style="margin-bottom:12px;display:flex;gap:8px">
            <el-button type="primary" size="small" @click="createBackup" :loading="backupCreating">立即备份</el-button>
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
          <p class="tab-hint">每日 02:00 自动全量备份（用例/套件/环境/数据集/凭据/账号），保留 30 天</p>
        </AppCard>
      </el-tab-pane>

      <el-tab-pane v-if="userStore.isAdmin" label="健康监控" name="monitor">
        <AppCard :hover="false" v-loading="monitorLoading">
          <div style="margin-bottom:12px;display:flex;gap:8px;align-items:center">
            <el-tag :type="monitorOverallType">{{ monitorOverallLabel }}</el-tag>
            <span class="tab-hint" style="margin:0">检测时间：{{ fmtTime(monitor.checked_at) }}</span>
            <el-button size="small" @click="loadMonitor">刷新</el-button>
          </div>
          <el-row :gutter="12">
            <el-col :span="8" v-for="card in monitorCards" :key="card.key">
              <div class="monitor-card">
                <div class="monitor-card-title">{{ card.title }}</div>
                <el-tag size="small" :type="statusTagType(card.status)">{{ card.status }}</el-tag>
                <div v-for="(val, key) in card.items" :key="key" class="monitor-row">
                  <span>{{ key }}</span><span>{{ val }}</span>
                </div>
              </div>
            </el-col>
          </el-row>
          <el-divider content-position="left">执行器故障自愈事件</el-divider>
          <el-table :data="executorEvents" size="small" stripe empty-text="暂无 failover 记录" max-height="200">
            <el-table-column prop="at" label="时间" width="170" />
            <el-table-column prop="from_url" label="原节点" show-overflow-tooltip />
            <el-table-column prop="to_url" label="切换至" show-overflow-tooltip />
            <el-table-column prop="task_id" label="任务" width="70" />
            <el-table-column prop="reason" label="原因" width="100" />
          </el-table>
        </AppCard>
      </el-tab-pane>

      <el-tab-pane v-if="userStore.isAdmin" label="安全审计" name="audit">
        <AppCard :hover="false">
          <el-table :data="auditLogs" stripe size="small" v-loading="auditLoading">
            <el-table-column prop="created_at" label="时间" width="170">
              <template #default="{ row }">{{ fmtTime(row.created_at) }}</template>
            </el-table-column>
            <el-table-column label="用户" width="120">
              <template #default="{ row }">{{ row.display_name || row.username || row.user_id }}</template>
            </el-table-column>
            <el-table-column prop="action" label="操作" width="120" />
            <el-table-column prop="resource_type" label="资源类型" width="110" />
            <el-table-column prop="resource_id" label="资源ID" width="100" show-overflow-tooltip />
            <el-table-column prop="detail" label="详情" show-overflow-tooltip />
            <el-table-column prop="ip" label="IP" width="120" />
          </el-table>
          <el-pagination
            v-model:current-page="auditPage"
            :page-size="20"
            :total="auditTotal"
            layout="total, prev, pager, next"
            style="margin-top:12px;justify-content:flex-end"
            @change="loadAuditLogs"
          />
        </AppCard>
      </el-tab-pane>

      <el-tab-pane label="账号池" name="accounts">
        <AppCard :hover="false">
          <div style="margin-bottom:12px"><el-button v-if="userStore.isAdmin" type="primary" size="small" @click="openAccount()">添加账号</el-button></div>
          <el-table :data="accounts" stripe size="small">
            <el-table-column prop="username" label="用户名" width="140" />
            <el-table-column prop="password_masked" label="密码" width="100" />
            <el-table-column prop="phone_masked" label="手机号" width="130" />
            <el-table-column prop="tags" label="标签" show-overflow-tooltip />
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

      <el-tab-pane label="回收站" name="recycle">
        <AppCard :hover="false">
          <div style="margin-bottom:12px">
            <el-button v-if="userStore.isAdmin" type="primary" size="small" :disabled="!selectedRecycleIds.length" @click="batchRestore">批量还原</el-button>
          </div>
          <el-table :data="recycleItems" stripe size="small" @selection-change="rows => selectedRecycleIds = rows.map(r => r.id)">
            <el-table-column v-if="userStore.isAdmin" type="selection" width="45" />
            <el-table-column prop="resource_type" label="类型" width="120">
              <template #default="{ row }">{{ resourceTypeLabel[row.resource_type] || row.resource_type }}</template>
            </el-table-column>
            <el-table-column prop="resource_name" label="名称" />
            <el-table-column prop="deleted_at" label="删除时间" width="170">
              <template #default="{ row }">{{ fmtTime(row.deleted_at) }}</template>
            </el-table-column>
            <el-table-column prop="expire_at" label="过期时间" width="170">
              <template #default="{ row }">{{ fmtTime(row.expire_at) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button v-if="userStore.isAdmin" size="small" type="primary" plain @click="restoreItem(row)">还原</el-button>
                <el-button v-if="userStore.isAdmin" size="small" type="danger" plain @click="recycleApi.purge(row.id).then(loadRecycle)">彻底删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </AppCard>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showEnvDialog" title="环境配置" width="520px">
      <el-form :model="envForm" label-width="90px">
        <el-form-item label="名称"><el-input v-model="envForm.name" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="envForm.env_type" style="width:100%">
            <el-option label="测试" value="test" /><el-option label="预发" value="staging" />
            <el-option label="灰度" value="gray" /><el-option label="生产" value="prod" />
          </el-select>
        </el-form-item>
        <el-form-item label="Base URL"><el-input v-model="envForm.base_url" /></el-form-item>
        <el-form-item label="变量 JSON">
          <el-input v-model="envForm.config_json" type="textarea" :rows="4" placeholder='{"token":"xxx","gray_device_tags":"gray-a,gray-b","allow_automation":false}' />
          <p class="form-hint">灰度环境可设 gray_device_tags；生产环境需 allow_automation=true 才允许自动化</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="btn-cancel" @click="showEnvDialog = false">取消</el-button>
        <el-button type="primary" @click="saveEnv">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDatasetDialog" title="数据集" width="520px">
      <el-form :model="datasetForm" label-width="90px">
        <el-form-item label="名称"><el-input v-model="datasetForm.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="datasetForm.description" /></el-form-item>
        <el-form-item label="数据行 JSON">
          <el-input v-model="datasetForm.rowsText" type="textarea" :rows="6" placeholder='[{"username":"u1","password":"***"}]' />
        </el-form-item>
        <el-form-item v-if="datasetForm.id" label="CSV 导入">
          <el-input v-model="datasetForm.csvText" type="textarea" :rows="4" placeholder="username,password&#10;user1,pass1" />
          <el-button size="small" style="margin-top:8px" @click="importDatasetCsv">导入 CSV</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="btn-cancel" @click="showDatasetDialog = false">取消</el-button>
        <el-button type="primary" @click="saveDataset">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showStepDialog" title="公共步骤" width="560px">
      <el-form :model="stepForm" label-width="90px">
        <el-form-item label="名称"><el-input v-model="stepForm.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="stepForm.description" /></el-form-item>
        <el-form-item label="步骤 JSON"><el-input v-model="stepForm.steps_content" type="textarea" :rows="6" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button class="btn-cancel" @click="showStepDialog = false">取消</el-button>
        <el-button type="primary" @click="saveStep">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showScheduleDialog" title="定时任务" width="480px">
      <el-form :model="scheduleForm" label-width="100px">
        <el-form-item label="名称"><el-input v-model="scheduleForm.name" /></el-form-item>
        <el-form-item label="套件ID">
          <el-select v-model="scheduleForm.suite_id" filterable placeholder="选择套件" style="width:100%">
            <el-option v-for="s in suiteOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Cron"><el-input v-model="scheduleForm.cron_expression" placeholder="0 0 2 * * ?" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button class="btn-cancel" @click="showScheduleDialog = false">取消</el-button>
        <el-button type="primary" @click="saveSchedule">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showAccountDialog" title="测试账号" width="480px">
      <el-form :model="accountForm" label-width="90px">
        <el-form-item label="用户名" required><el-input v-model="accountForm.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="accountForm.password" type="password" placeholder="留空则不修改" show-password /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="accountForm.phone" /></el-form-item>
        <el-form-item label="标签"><el-input v-model="accountForm.tags" placeholder="冒烟,回归" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="accountForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button class="btn-cancel" @click="showAccountDialog = false">取消</el-button>
        <el-button type="primary" @click="saveAccount">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showCommentDialog" :title="`协同批注 · ${commentAssetName}`" width="560px">
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

    <el-dialog v-model="showBaselineDialog" title="版本基线" width="560px">
      <el-form :model="baselineForm" label-width="100px">
        <el-form-item label="名称" required><el-input v-model="baselineForm.name" /></el-form-item>
        <el-form-item label="版本标签"><el-input v-model="baselineForm.version_label" placeholder="v2.1.0" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="baselineForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="APP包ID"><el-input v-model="baselineForm.app_package_id" placeholder="可选" /></el-form-item>
        <el-form-item label="套件ID"><el-input v-model="baselineForm.suite_id" placeholder="可选" /></el-form-item>
        <el-form-item label="环境ID"><el-input v-model="baselineForm.env_id" placeholder="可选" /></el-form-item>
        <el-form-item label="配置 JSON"><el-input v-model="baselineForm.config_json" type="textarea" :rows="4" placeholder='{"min_app_version":"1.0.0"}' /></el-form-item>
      </el-form>
      <template #footer>
        <el-button class="btn-cancel" @click="showBaselineDialog = false">取消</el-button>
        <el-button type="primary" @click="saveBaseline">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showTeamDialog" title="团队" width="480px">
      <el-form :model="teamForm" label-width="80px">
        <el-form-item label="名称" required><el-input v-model="teamForm.name" /></el-form-item>
        <el-form-item label="编码" required><el-input v-model="teamForm.code" :disabled="!!teamForm.id" placeholder="default" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="teamForm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button class="btn-cancel" @click="showTeamDialog = false">取消</el-button>
        <el-button type="primary" @click="saveTeam">保存</el-button>
      </template>
    </el-dialog>

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

    <el-dialog v-model="showGlobalParamDialog" title="全局参数" width="520px">
      <el-form :model="globalParamForm" label-width="90px">
        <el-form-item label="参数键" required><el-input v-model="globalParamForm.param_key" placeholder="API_HOST" /></el-form-item>
        <el-form-item label="作用域">
          <el-select v-model="globalParamForm.scope" style="width:100%">
            <el-option label="平台全局" value="platform" />
            <el-option label="环境级" value="env" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="globalParamForm.scope === 'env'" label="环境ID">
          <el-input v-model="globalParamForm.env_id" placeholder="环境 ID" />
        </el-form-item>
        <el-form-item :label="globalParamForm.id ? '新值' : '值'">
          <el-input v-model="globalParamForm.param_value" :type="globalParamForm.sensitive ? 'password' : 'text'" show-password />
        </el-form-item>
        <el-form-item label="敏感"><el-switch v-model="globalParamForm.sensitive" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="globalParamForm.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item v-if="globalParamForm.id" label="变更说明"><el-input v-model="globalParamForm.change_note" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button class="btn-cancel" @click="showGlobalParamDialog = false">取消</el-button>
        <el-button type="primary" @click="saveGlobalParam">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showGlobalParamLogDialog" title="参数变更日志" width="640px">
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

    <el-dialog v-model="showAssertPolicyDialog" title="断言策略" width="520px">
      <el-form :model="assertPolicyForm" label-width="90px">
        <el-form-item label="规则类型">
          <el-select v-model="assertPolicyForm.rule_type" style="width:100%">
            <el-option label="白名单(失败跳过)" value="whitelist" />
            <el-option label="黑名单(不执行)" value="blacklist" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标类型">
          <el-select v-model="assertPolicyForm.target_type" style="width:100%">
            <el-option label="断言类型" value="assert_type" />
            <el-option label="控件名" value="element_name" />
            <el-option label="Toast文本" value="toast_pattern" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配模式"><el-input v-model="assertPolicyForm.pattern" placeholder="assert_toast 或 *popup*" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="assertPolicyForm.description" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="assertPolicyForm.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button class="btn-cancel" @click="showAssertPolicyDialog = false">取消</el-button>
        <el-button type="primary" @click="saveAssertPolicy">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDataFactoryDialog" title="造数模板" width="640px">
      <el-form :model="dataFactoryForm" label-width="110px">
        <el-form-item label="名称" required><el-input v-model="dataFactoryForm.name" /></el-form-item>
        <el-form-item label="HTTP方法"><el-input v-model="dataFactoryForm.method" placeholder="POST" /></el-form-item>
        <el-form-item label="URL模板" required><el-input v-model="dataFactoryForm.url_template" placeholder="https://api.test/{{BASE_URL}}/orders" /></el-form-item>
        <el-form-item label="Headers JSON"><el-input v-model="dataFactoryForm.headers_json" type="textarea" :rows="2" placeholder='{"Authorization":"Bearer {{TOKEN}}"}' /></el-form-item>
        <el-form-item label="Body模板"><el-input v-model="dataFactoryForm.body_template" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="变量提取"><el-input v-model="dataFactoryForm.extract_json" type="textarea" :rows="2" placeholder='{"order_id":"/data/id"}' /></el-form-item>
        <el-form-item label="清理方法"><el-input v-model="dataFactoryForm.cleanup_method" placeholder="DELETE" /></el-form-item>
        <el-form-item label="清理URL"><el-input v-model="dataFactoryForm.cleanup_url_template" placeholder="https://api.test/orders/{{order_id}}" /></el-form-item>
        <el-form-item label="清理Body"><el-input v-model="dataFactoryForm.cleanup_body_template" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="dataFactoryForm.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button class="btn-cancel" @click="showDataFactoryDialog = false">取消</el-button>
        <el-button type="primary" @click="saveDataFactory">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showCredentialDialog" title="加密凭据" width="480px">
      <el-form :model="credentialForm" label-width="90px">
        <el-form-item label="名称" required><el-input v-model="credentialForm.name" placeholder="API_TOKEN" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="credentialForm.category" placeholder="api/db" /></el-form-item>
        <el-form-item label="环境ID"><el-input v-model="credentialForm.env_id" placeholder="可选" /></el-form-item>
        <el-form-item :label="credentialForm.id ? '新值' : '值'" :required="!credentialForm.id">
          <el-input v-model="credentialForm.value" type="password" show-password placeholder="留空则不修改" />
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="credentialForm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button class="btn-cancel" @click="showCredentialDialog = false">取消</el-button>
        <el-button type="primary" @click="saveCredential">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showTransferDialog" title="资产移交" width="420px">
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
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { envApi, datasetApi, commonStepApi, scheduleApi, recycleApi, suiteApi, authApi, accountApi, commentApi, baselineApi, auditApi, credentialApi, backupApi, monitorApi, teamApi, globalParamApi, assertPolicyApi, dataFactoryApi, recordingApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { invalidateRecordingFeatures } from '@/composables/useRecordingFeatures'
import { formatTime as fmtTime } from '@/utils/status'
import { ElMessage, ElMessageBox } from 'element-plus'

const userStore = useUserStore()
const activeTab = ref('env')
const envs = ref([])
const datasets = ref([])
const commonSteps = ref([])
const schedules = ref([])
const baselines = ref([])
const teams = ref([])
const showTeamDialog = ref(false)
const teamForm = reactive({ id: null, name: '', code: '', description: '' })
const credentials = ref([])
const backups = ref([])
const backupCreating = ref(false)
const monitor = ref({})
const monitorLoading = ref(false)
const executorEvents = ref([])
const auditLogs = ref([])
const auditLoading = ref(false)
const auditPage = ref(1)
const auditTotal = ref(0)
const accounts = ref([])
const recycleItems = ref([])
const selectedRecycleIds = ref([])
const selectedStepIds = ref([])
const userOptions = ref([])
const showTransferDialog = ref(false)
const transferOwnerId = ref(null)
const resourceTypeLabel = { test_case: '用例', test_suite: '套件', common_step: '公共步骤', data_set: '数据集' }
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
const envForm = reactive({ id: null, name: '', env_type: 'test', base_url: '', config_json: '{}' })
const showDatasetDialog = ref(false)
const datasetForm = reactive({ id: null, name: '', description: '', rowsText: '[]', csvText: '' })
const showStepDialog = ref(false)
const stepForm = reactive({ id: null, name: '', description: '', steps_content: '{"steps":[]}' })
const showScheduleDialog = ref(false)
const showAccountDialog = ref(false)
const scheduleForm = reactive({ id: null, name: '', suite_id: null, cron_expression: '0 0 2 * * ?' })
const accountForm = reactive({ id: null, username: '', password: '', phone: '', tags: '', remark: '' })
const showCommentDialog = ref(false)
const commentAssetType = ref('common_step')
const commentAssetId = ref(null)
const commentAssetName = ref('')
const comments = ref([])
const newComment = ref('')
const showBaselineDialog = ref(false)
const showBaselineCompareDialog = ref(false)
const baselineCompareResult = ref(null)
const baselineForm = reactive({ id: null, name: '', version_label: '', description: '', app_package_id: '', suite_id: '', env_id: '', config_json: '{}' })
const showCredentialDialog = ref(false)
const credentialForm = reactive({ id: null, name: '', category: '', env_id: null, description: '', value: '' })
const globalParams = ref([])
const showGlobalParamDialog = ref(false)
const showGlobalParamLogDialog = ref(false)
const globalParamLogs = ref([])
const globalParamForm = reactive({
  id: null, param_key: '', scope: 'platform', env_id: null, param_value: '',
  sensitive: false, description: '', change_note: ''
})
const assertPolicies = ref([])
const showAssertPolicyDialog = ref(false)
const assertPolicyForm = reactive({
  id: null, rule_type: 'whitelist', target_type: 'assert_type', pattern: '', description: '', enabled: true
})
const dataFactoryTemplates = ref([])
const showDataFactoryDialog = ref(false)
const dataFactoryForm = reactive({
  id: null, name: '', method: 'POST', url_template: '', headers_json: '{}', body_template: '',
  extract_json: '{}', cleanup_method: 'DELETE', cleanup_url_template: '', cleanup_body_template: '', enabled: true
})

async function loadEnvs() { envs.value = (await envApi.list()).data }
async function loadDatasets() { datasets.value = (await datasetApi.list()).data }
async function loadSteps() { commonSteps.value = (await commonStepApi.list()).data }
async function loadSchedules() { schedules.value = (await scheduleApi.list()).data }
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
async function loadAssertPolicies() {
  if (!userStore.isAdmin) return
  try { assertPolicies.value = (await assertPolicyApi.list()).data } catch { assertPolicies.value = [] }
}
async function loadDataFactoryTemplates() {
  if (!userStore.isAdmin) return
  try { dataFactoryTemplates.value = (await dataFactoryApi.listTemplates()).data } catch { dataFactoryTemplates.value = [] }
}
async function loadBackups() {
  if (!userStore.isAdmin) return
  try { backups.value = (await backupApi.list()).data } catch { backups.value = [] }
}

const monitorOverallType = computed(() => ({ healthy: 'success', degraded: 'warning', critical: 'danger' }[monitor.value.overall] || 'info'))
const monitorOverallLabel = computed(() => ({ healthy: '运行正常', degraded: '部分异常', critical: '严重异常' }[monitor.value.overall] || '未知'))

const monitorCards = computed(() => {
  const m = monitor.value
  if (!m.backend) return []
  const fmtBytes = (b) => b != null ? (b / 1048576).toFixed(1) + ' MB' : '-'
  return [
    { key: 'db', title: '数据库', status: m.database?.status, items: { 类型: m.database?.type || '-', 错误: m.database?.error || '无' } },
    { key: 'exec', title: '执行器', status: m.executor?.status, items: {
      主节点: m.executor?.url || '-',
      健康节点: m.executor_pool ? `${m.executor_pool.healthy_count}/${m.executor_pool.total_count}` : '-',
      错误: m.executor?.error || '无'
    } },
    { key: 'store', title: '存储', status: m.storage?.status, items: {
      录屏: fmtBytes(m.storage?.recordings_bytes),
      报告: fmtBytes(m.storage?.reports_bytes),
      合计: fmtBytes(m.storage?.total_bytes),
      使用率: m.storage?.usage_percent != null ? m.storage.usage_percent.toFixed(1) + '%' : '-',
      告警: m.storage?.alert || '无'
    } },
    { key: 'dev', title: '设备池', status: 'up', items: { 在线: m.devices?.online, 占用: m.devices?.busy, 离线: m.devices?.offline, 异常: m.devices?.error } },
    { key: 'sched', title: '调度队列', status: 'up', items: { 排队: m.scheduler?.queue_size, 运行中: m.scheduler?.running_tasks, 待调度: m.scheduler?.queued_tasks } },
    { key: 'be', title: '后端服务', status: m.backend?.status, items: { 服务: m.backend?.service || '-' } }
  ]
})

function statusTagType(s) {
  return { up: 'success', down: 'danger', warn: 'warning' }[s] || 'info'
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
  }
  finally { monitorLoading.value = false }
}
async function loadAuditLogs() {
  if (!userStore.isAdmin) return
  auditLoading.value = true
  try {
    const res = await auditApi.list({ page: auditPage.value, page_size: 20 })
    auditLogs.value = res.data.list
    auditTotal.value = res.data.total
  } catch {
    auditLogs.value = []
    auditTotal.value = 0
  } finally {
    auditLoading.value = false
  }
}
async function loadAccounts() { accounts.value = (await accountApi.list()).data }
async function loadRecycle() { recycleItems.value = (await recycleApi.list()).data }
async function loadUsers() {
  if (userStore.isAdmin) {
    userOptions.value = (await authApi.listUsers()).data
  }
}

async function restoreItem(row) {
  await recycleApi.restore(row.id)
  ElMessage.success('已还原')
  loadRecycle()
}

async function batchRestore() {
  const res = await recycleApi.batchRestore(selectedRecycleIds.value)
  ElMessage.success(`已还原 ${res.data.restored} 项`)
  if (res.data.errors?.length) ElMessage.warning(res.data.errors.join('; '))
  selectedRecycleIds.value = []
  loadRecycle()
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

function openEnv(row) {
  if (row) Object.assign(envForm, { id: row.id, name: row.name, env_type: row.env_type, base_url: row.base_url, config_json: row.config_json || '{}' })
  else Object.assign(envForm, { id: null, name: '', env_type: 'test', base_url: '', config_json: '{}' })
  showEnvDialog.value = true
}

async function saveEnv() {
  const payload = { name: envForm.name, env_type: envForm.env_type, base_url: envForm.base_url, config_json: envForm.config_json }
  if (envForm.id) await envApi.update(envForm.id, payload)
  else await envApi.create(payload)
  showEnvDialog.value = false
  loadEnvs()
}

function openDataset(row) {
  if (row) {
    datasetApi.get(row.id).then(res => {
      const ds = res.data.dataset
      datasetForm.id = ds.id
      datasetForm.name = ds.name
      datasetForm.description = ds.description || ''
      const rows = (res.data.rows || []).map(r => {
        try { return JSON.parse(r.row_data_json || '{}') } catch { return {} }
      })
      datasetForm.rowsText = JSON.stringify(rows, null, 2)
      showDatasetDialog.value = true
    })
  } else {
    Object.assign(datasetForm, { id: null, name: '', description: '', rowsText: '[]' })
    showDatasetDialog.value = true
  }
}

async function saveDataset() {
  let rows = []
  try { rows = JSON.parse(datasetForm.rowsText) } catch { ElMessage.error('JSON 格式错误'); return }
  const payload = { name: datasetForm.name, description: datasetForm.description, rows }
  if (datasetForm.id) await datasetApi.update(datasetForm.id, payload)
  else await datasetApi.create(payload)
  showDatasetDialog.value = false
  loadDatasets()
}

async function importDatasetCsv() {
  if (!datasetForm.id || !datasetForm.csvText?.trim()) {
    ElMessage.warning('请先保存数据集并填写 CSV')
    return
  }
  const res = await datasetApi.importCsv(datasetForm.id, datasetForm.csvText)
  ElMessage.success(`已导入 ${res.data.imported} 行`)
  datasetForm.csvText = ''
  openDataset({ id: datasetForm.id })
}

async function deleteDataset(row) {
  await ElMessageBox.confirm('确定删除该数据集？', '确认', { type: 'warning' })
  await datasetApi.delete(row.id)
  ElMessage.success('已删除')
  loadDatasets()
}

function openStep(row) {
  if (row) Object.assign(stepForm, row)
  else Object.assign(stepForm, { id: null, name: '', description: '', steps_content: '{"steps":[]}' })
  showStepDialog.value = true
}

async function saveStep() {
  const payload = { name: stepForm.name, description: stepForm.description, steps_content: stepForm.steps_content }
  if (stepForm.id) await commonStepApi.update(stepForm.id, payload)
  else await commonStepApi.create(payload)
  showStepDialog.value = false
  loadSteps()
}

async function deleteStep(row) {
  try {
    await commonStepApi.delete(row.id)
    loadSteps()
  } catch { /* dependency error shown by interceptor */ }
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
  if (row) {
    Object.assign(baselineForm, {
      id: row.id, name: row.name, version_label: row.version_label || '',
      description: row.description || '', app_package_id: row.app_package_id || '',
      suite_id: row.suite_id || '', env_id: row.env_id || '',
      config_json: row.config_json || '{}'
    })
  } else {
    Object.assign(baselineForm, { id: null, name: '', version_label: '', description: '', app_package_id: '', suite_id: '', env_id: '', config_json: '{}' })
  }
  showBaselineDialog.value = true
}

async function saveBaseline() {
  if (!baselineForm.name?.trim()) { ElMessage.warning('请填写名称'); return }
  const payload = {
    name: baselineForm.name.trim(),
    version_label: baselineForm.version_label,
    description: baselineForm.description,
    config_json: baselineForm.config_json
  }
  if (baselineForm.app_package_id) payload.app_package_id = Number(baselineForm.app_package_id)
  if (baselineForm.suite_id) payload.suite_id = Number(baselineForm.suite_id)
  if (baselineForm.env_id) payload.env_id = Number(baselineForm.env_id)
  if (baselineForm.id) await baselineApi.update(baselineForm.id, payload)
  else await baselineApi.create(payload)
  showBaselineDialog.value = false
  loadBaselines()
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
  if (row) {
    Object.assign(teamForm, { id: row.id, name: row.name, code: row.code, description: row.description || '' })
  } else {
    Object.assign(teamForm, { id: null, name: '', code: '', description: '' })
  }
  showTeamDialog.value = true
}

async function saveTeam() {
  if (!teamForm.name?.trim() || !teamForm.code?.trim()) { ElMessage.warning('请填写名称和编码'); return }
  const payload = { name: teamForm.name.trim(), code: teamForm.code.trim(), description: teamForm.description }
  if (teamForm.id) await teamApi.update(teamForm.id, payload)
  else await teamApi.create(payload)
  ElMessage.success('团队已保存')
  showTeamDialog.value = false
  loadTeams()
}

async function assignUserTeam(userId, teamId) {
  await teamApi.assignUser(userId, teamId)
  ElMessage.success('用户团队已更新')
  loadUsers()
}

function openCredential(row) {
  if (row) {
    Object.assign(credentialForm, { id: row.id, name: row.name, category: row.category || '', env_id: row.env_id, description: row.description || '', value: '' })
  } else {
    Object.assign(credentialForm, { id: null, name: '', category: '', env_id: null, description: '', value: '' })
  }
  showCredentialDialog.value = true
}

async function saveCredential() {
  if (!credentialForm.name?.trim()) { ElMessage.warning('请填写名称'); return }
  const payload = { name: credentialForm.name, category: credentialForm.category, description: credentialForm.description, env_id: credentialForm.env_id }
  if (credentialForm.value) payload.value = credentialForm.value
  if (credentialForm.id) await credentialApi.update(credentialForm.id, payload)
  else {
    if (!credentialForm.value) { ElMessage.warning('请填写凭据值'); return }
    await credentialApi.create(payload)
  }
  showCredentialDialog.value = false
  loadCredentials()
}

async function deleteCredential(row) {
  await ElMessageBox.confirm(`删除凭据「${row.name}」？`, '确认', { type: 'warning' })
  await credentialApi.delete(row.id)
  loadCredentials()
}

function openGlobalParam(row) {
  if (row) {
    Object.assign(globalParamForm, {
      id: row.id, param_key: row.param_key, scope: row.scope || 'platform',
      env_id: row.env_id, param_value: '', sensitive: row.sensitive || false,
      description: row.description || '', change_note: ''
    })
  } else {
    Object.assign(globalParamForm, {
      id: null, param_key: '', scope: 'platform', env_id: null, param_value: '',
      sensitive: false, description: '', change_note: ''
    })
  }
  showGlobalParamDialog.value = true
}

async function saveGlobalParam() {
  if (!globalParamForm.param_key?.trim()) { ElMessage.warning('请填写参数键'); return }
  const payload = {
    param_key: globalParamForm.param_key.trim(),
    scope: globalParamForm.scope,
    sensitive: globalParamForm.sensitive,
    description: globalParamForm.description,
    enabled: true
  }
  if (globalParamForm.scope === 'env' && globalParamForm.env_id) payload.env_id = Number(globalParamForm.env_id)
  if (globalParamForm.param_value) payload.param_value = globalParamForm.param_value
  if (globalParamForm.id) {
    payload.change_note = globalParamForm.change_note || '更新'
    await globalParamApi.update(globalParamForm.id, payload)
  } else {
    if (!globalParamForm.param_value) { ElMessage.warning('请填写参数值'); return }
    await globalParamApi.create(payload)
  }
  showGlobalParamDialog.value = false
  loadGlobalParams()
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
  if (row) {
    Object.assign(assertPolicyForm, { id: row.id, rule_type: row.rule_type, target_type: row.target_type, pattern: row.pattern, description: row.description || '', enabled: row.enabled !== false })
  } else {
    Object.assign(assertPolicyForm, { id: null, rule_type: 'whitelist', target_type: 'assert_type', pattern: '', description: '', enabled: true })
  }
  showAssertPolicyDialog.value = true
}

async function saveAssertPolicy() {
  if (!assertPolicyForm.pattern?.trim()) { ElMessage.warning('请填写匹配模式'); return }
  const payload = { ...assertPolicyForm }
  delete payload.id
  if (assertPolicyForm.id) await assertPolicyApi.update(assertPolicyForm.id, payload)
  else await assertPolicyApi.create(payload)
  showAssertPolicyDialog.value = false
  loadAssertPolicies()
}

async function deleteAssertPolicy(row) {
  await ElMessageBox.confirm('确定删除该规则？', '确认', { type: 'warning' })
  await assertPolicyApi.delete(row.id)
  loadAssertPolicies()
}

function openDataFactory(row) {
  if (row) {
    Object.assign(dataFactoryForm, {
      id: row.id, name: row.name, method: row.method || 'POST', url_template: row.url_template || '',
      headers_json: row.headers_json || '{}', body_template: row.body_template || '',
      extract_json: row.extract_json || '{}', cleanup_method: row.cleanup_method || 'DELETE',
      cleanup_url_template: row.cleanup_url_template || '', cleanup_body_template: row.cleanup_body_template || '',
      enabled: row.enabled !== false
    })
  } else {
    Object.assign(dataFactoryForm, {
      id: null, name: '', method: 'POST', url_template: '', headers_json: '{}', body_template: '',
      extract_json: '{}', cleanup_method: 'DELETE', cleanup_url_template: '', cleanup_body_template: '', enabled: true
    })
  }
  showDataFactoryDialog.value = true
}

async function saveDataFactory() {
  if (!dataFactoryForm.name?.trim() || !dataFactoryForm.url_template?.trim()) {
    ElMessage.warning('请填写名称和 URL'); return
  }
  const payload = { ...dataFactoryForm }
  delete payload.id
  if (dataFactoryForm.id) await dataFactoryApi.updateTemplate(dataFactoryForm.id, payload)
  else await dataFactoryApi.createTemplate(payload)
  showDataFactoryDialog.value = false
  loadDataFactoryTemplates()
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
  if (row) {
    Object.assign(scheduleForm, { id: row.id, name: row.name, suite_id: row.suite_id, cron_expression: row.cron_expression })
  } else {
    Object.assign(scheduleForm, { id: null, name: '', suite_id: suiteOptions.value[0]?.id || null, cron_expression: '0 0 2 * * ?' })
  }
  showScheduleDialog.value = true
}

async function saveSchedule() {
  if (!scheduleForm.suite_id) {
    ElMessage.warning('请选择套件')
    return
  }
  const payload = { name: scheduleForm.name, suite_id: scheduleForm.suite_id, cron_expression: scheduleForm.cron_expression, enabled: true }
  if (scheduleForm.id) await scheduleApi.update(scheduleForm.id, payload)
  else await scheduleApi.create(payload)
  showScheduleDialog.value = false
  loadSchedules()
}

async function deleteSchedule(row) {
  await ElMessageBox.confirm('确定删除该定时任务？', '确认', { type: 'warning' })
  await scheduleApi.delete(row.id)
  ElMessage.success('已删除')
  loadSchedules()
}

function openAccount(row) {
  if (row) {
    Object.assign(accountForm, { id: row.id, username: row.username, password: '', phone: '', tags: row.tags || '', remark: row.remark || '' })
  } else {
    Object.assign(accountForm, { id: null, username: '', password: '', phone: '', tags: '', remark: '' })
  }
  showAccountDialog.value = true
}

async function saveAccount() {
  if (!accountForm.username?.trim()) { ElMessage.warning('请填写用户名'); return }
  const payload = { username: accountForm.username, phone: accountForm.phone, tags: accountForm.tags, remark: accountForm.remark }
  if (accountForm.password) payload.password = accountForm.password
  if (accountForm.id) await accountApi.update(accountForm.id, payload)
  else await accountApi.create(payload)
  showAccountDialog.value = false
  loadAccounts()
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
  loadEnvs(); loadDatasets(); loadSteps(); loadSchedules(); loadBaselines(); loadTeams()
  loadGlobalParams(); loadAssertPolicies(); loadDataFactoryTemplates(); loadCredentials()
  loadBackups(); loadMonitor(); loadAuditLogs(); loadAccounts(); loadRecycle()
  loadSuiteOptions(); loadUsers(); loadRecordingFeatures()
})
</script>

<style scoped>
.core-tabs :deep(.el-tabs__header) { margin-bottom: 16px; }
.comment-list { max-height: 280px; overflow-y: auto; }
.comment-empty { color: var(--atp-text-secondary); font-size: 13px; padding: 12px 0; text-align: center; }
.comment-item { padding: 10px 0; border-bottom: 1px solid var(--atp-border-neutral); }
.comment-meta { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--atp-text-secondary); margin-bottom: 4px; }
.comment-author { font-weight: 600; }
.comment-time { flex: 1; }
.comment-body { font-size: 14px; line-height: 1.5; white-space: pre-wrap; }
.tab-hint { margin-top: 10px; font-size: 12px; color: var(--atp-text-secondary); }
.form-hint { margin: 4px 0 0; font-size: 12px; color: var(--atp-text-secondary); line-height: 1.5; }
.monitor-card { border: 1px solid var(--atp-border-neutral); border-radius: var(--atp-radius); padding: 12px; margin-bottom: 12px; min-height: 120px; }
.monitor-card-title { font-weight: 600; margin-bottom: 8px; }
.monitor-row { display: flex; justify-content: space-between; font-size: 12px; color: var(--atp-text-secondary); margin-top: 6px; }
</style>
