<template>
  <div class="page-container vce-page">
    <PageHeader
      :title="taskId ? '编辑可视化用例' : '新建可视化用例'"
      subtitle="拖拽式可视化步骤编排，自动生成自动化执行脚本"
    >
      <template #actions>
        <div class="header-actions">
          <el-button class="btn-secondary" @click="previewScript">预览脚本</el-button>

          <el-dropdown split-button type="warning" :disabled="debugging" @click="debugRun('full')" @command="onDebugCommand">
            {{ debugging ? '调试中…' : '调试执行' }}
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="full">完整调试</el-dropdown-item>
                <el-dropdown-item command="step">单步调试</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <el-dropdown split-button type="primary" :disabled="saving" @click="saveTask('keep')" @command="onSaveCommand">
            {{ saving ? '保存中…' : '保存' }}
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="draft">保存草稿</el-dropdown-item>
                <el-dropdown-item command="active">提交生效</el-dropdown-item>
                <el-dropdown-item command="template">另存为模板</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <el-button @click="$router.back()">返回</el-button>
        </div>
      </template>
    </PageHeader>

    <el-row :gutter="16" class="main-row">
      <!-- 模块 2：用例信息 -->
      <el-col :xs="24" :lg="10">
        <AppCard title="用例信息" :hover="false" class="meta-card">
          <el-form :model="meta" label-width="110px" class="meta-form">
            <div class="form-group">
              <div class="form-group-title">基础信息（必配项）</div>

              <el-form-item required>
                <template #label>
                  <span v-tooltip="'用于标识业务场景的中文名称，便于检索与评审'">用例名称</span>
                </template>
                <el-input
                  v-model="meta.name"
                  placeholder="请填写中文业务名称，如登录流程、支付校验"
                  maxlength="80"
                  show-word-limit
                />
              </el-form-item>

              <el-form-item>
                <template #label>
                  <span v-tooltip="'将用例归入目录，方便批量管理与筛选'">所属目录</span>
                </template>
                <el-select v-model="meta.folder_id" clearable placeholder="根目录（全部用例）" style="width:100%">
                  <el-option label="根目录（全部用例）" :value="null" />
                  <el-option v-for="f in flatFolders" :key="f.id" :label="f.label" :value="f.id" />
                </el-select>
              </el-form-item>

              <el-form-item>
                <template #label>
                  <span v-tooltip="'选择用例执行所依赖的移动端操作系统'">运行平台</span>
                </template>
                <el-select v-model="meta.platform" style="width:100%">
                  <el-option label="安卓" value="android" />
                  <el-option label="iOS" value="ios" />
                </el-select>
              </el-form-item>

              <el-form-item>
                <template #label>
                  <span v-tooltip="'被测应用包名，可从设备池复制后粘贴至此'">应用包名</span>
                </template>
                <div class="pkg-row">
                  <el-input v-model="meta.app_package" placeholder="com.example.app" />
                  <el-button @click="goDevicePool">快捷填充</el-button>
                </div>
              </el-form-item>

              <el-form-item>
                <template #label>
                  <span v-tooltip="'单次执行最长等待时间，超时后任务将终止'">全局超时 (秒)</span>
                </template>
                <el-input-number v-model="meta.timeout_seconds" :min="60" :max="7200" :step="60" style="width:100%" />
              </el-form-item>
            </div>

            <div class="form-group form-group--advanced">
              <div class="form-group-title">高级执行配置（可选）</div>

              <el-form-item>
                <template #label>
                  <span v-tooltip="'执行全程录制设备画面，生成回放视频'">录屏</span>
                </template>
                <div class="switch-with-hint">
                  <el-switch v-model="meta.enable_recording" />
                  <span class="field-hint">执行全程录制设备画面，生成回放视频</span>
                </div>
              </el-form-item>

              <el-form-item>
                <template #label>
                  <span v-tooltip="'模拟真人操作速率，避免检测自动化脚本'">真人模拟</span>
                </template>
                <div class="switch-with-hint">
                  <el-switch v-model="meta.human_delay" />
                  <span class="field-hint">模拟真人操作速率，避免检测自动化脚本</span>
                </div>
              </el-form-item>

              <el-form-item>
                <template #label>
                  <span v-tooltip="'统一控制步骤间等待节奏：标准 / 快速 / 慢速'">等待模板</span>
                </template>
                <el-select v-model="meta.wait_template" clearable placeholder="标准" style="width:100%">
                  <el-option label="标准" value="standard" />
                  <el-option label="快速" value="smoke" />
                  <el-option label="慢速" value="weak_network" />
                </el-select>
              </el-form-item>

              <el-form-item v-if="isAssetMode">
                <template #label>
                  <span v-tooltip="'草稿可继续编辑；待评审等待审核；已生效可被套件引用执行'">用例状态</span>
                </template>
                <el-select v-model="meta.case_status" style="width:100%">
                  <el-option label="草稿" value="draft" />
                  <el-option label="待评审" value="review" />
                  <el-option label="已生效" value="active" />
                </el-select>
              </el-form-item>

              <el-form-item v-if="!isAssetMode" label="账号池互斥">
                <el-switch v-model="meta.use_account_pool" />
              </el-form-item>
            </div>
          </el-form>
        </AppCard>
      </el-col>

      <!-- 模块 3：步骤列表 -->
      <el-col :xs="24" :lg="14">
        <AppCard :hover="false" class="steps-card">
          <template #header>
            <div class="steps-header">
              <div class="steps-header-left">
                <span class="steps-title">测试步骤列表</span>
                <span class="steps-hint">拖拽步骤调整执行顺序</span>
              </div>
              <div class="steps-header-actions">
                <el-tooltip content="请先勾选步骤" :disabled="hasStepSelection" placement="top">
                  <span>
                    <el-button size="small" :disabled="!hasStepSelection" @click="batchCopySteps">批量复制</el-button>
                  </span>
                </el-tooltip>
                <el-tooltip content="请先勾选步骤" :disabled="hasStepSelection" placement="top">
                  <span>
                    <el-button size="small" type="danger" plain :disabled="!hasStepSelection" @click="batchDeleteSteps">批量删除</el-button>
                  </span>
                </el-tooltip>
                <el-button size="small" type="primary" plain @click="showImportCommon = true">导入公共步骤</el-button>
                <el-button size="small" @click="addBranchStep">添加分支判断</el-button>
                <el-button size="small" @click="addLoopStep">添加循环步骤</el-button>
              </div>
            </div>
          </template>

          <div class="steps-body">
            <div class="steps-toolbar">
              <el-input
                v-model="stepKeyword"
                clearable
                prefix-icon="Search"
                placeholder="搜索步骤类型 / 摘要 / 控件"
                style="max-width:280px"
              />
              <span class="steps-count">共 {{ filteredSteps.length }} / {{ steps.length }} 步</span>
            </div>

            <div class="steps-scroll">
              <div v-if="!steps.length" class="steps-empty">
                <p class="empty-title">暂无测试步骤</p>
                <div class="empty-actions">
                  <el-button type="primary" @click="focusAddPanel('wait')">① 快速添加基础操作</el-button>
                  <el-button @click="showImportCommon = true">② 从公共步骤库导入</el-button>
                  <el-button @click="goElementPicker">③ 打开控件拾取插入控件操作</el-button>
                </div>
              </div>

              <div v-else class="steps-list">
                <div
                  v-for="item in filteredStepItems"
                  :key="item.step.id"
                  class="step-item"
                  :class="[stepToneClass(item.step.type), { disabled: item.step.enabled === false, selected: selectedStepIds.has(item.step.id) }]"
                >
                  <el-checkbox
                    :model-value="selectedStepIds.has(item.step.id)"
                    @change="(v) => toggleStepSelect(item.step.id, v)"
                  />
                  <div class="step-index">{{ item.index + 1 }}</div>
                  <div class="step-body">
                    <el-switch v-model="item.step.enabled" size="small" />
                    <el-tag size="small" :type="stepTagType(item.step.type)" effect="plain">
                      {{ stepTypeLabel(item.step.type) }}
                    </el-tag>
                    <span class="step-desc">{{ stepSummary(item.step) }}</span>
                    <span v-if="stepLocator(item.step)" class="step-locator">{{ stepLocator(item.step) }}</span>
                    <el-tag v-if="item.step.enabled === false" size="small" type="info">
                      {{ item.step.disable_reason || '已禁用' }}
                    </el-tag>
                  </div>
                  <div class="step-actions">
                    <el-button size="small" type="primary" plain @click="editStep(item.index)">编辑</el-button>
                    <el-button size="small" class="btn-copy" @click="copyStep(item.index)">复制</el-button>
                    <el-button size="small" plain :disabled="item.index === 0" @click="moveStep(item.index, -1)">上移</el-button>
                    <el-button size="small" plain :disabled="item.index === steps.length - 1" @click="moveStep(item.index, 1)">下移</el-button>
                    <el-button size="small" type="danger" plain @click="removeStep(item.index)">删除</el-button>
                  </div>
                </div>
                <el-empty v-if="!filteredStepItems.length" description="未找到匹配步骤" :image-size="64" />
              </div>
            </div>
          </div>
        </AppCard>
      </el-col>
    </el-row>

    <!-- 模块 4：底部添加步骤通栏 -->
    <AppCard :hover="false" class="add-panel">
      <template #header>
        <div class="add-panel-header">
          <span>{{ editingIndex !== null ? `编辑第 ${editingIndex + 1} 步` : '添加步骤' }}</span>
          <el-button v-if="editingIndex !== null" size="small" text type="primary" @click="cancelEdit">取消编辑</el-button>
        </div>
      </template>

      <div class="quick-section">
        <div class="quick-group">
          <span class="quick-label">应用操作类</span>
          <div class="quick-btns">
            <el-button size="small" @click="quickAddStep('clear_cache', { mode: 'disk' })">清理磁盘缓存</el-button>
            <el-button size="small" @click="quickAddStep('clear_cache', { mode: 'memory' })">杀进程</el-button>
            <el-button size="small" @click="quickAddStep('clear_cache', { mode: 'all' })">清理全部缓存</el-button>
            <el-button size="small" @click="quickAddStep('force_stop')">强制停止应用</el-button>
            <el-button size="small" @click="quickAddStep('launch')">启动应用</el-button>
            <el-button size="small" @click="quickAddStep('install_apk')">安装包</el-button>
          </div>
        </div>
        <div class="quick-group">
          <span class="quick-label">控件操作类</span>
          <div class="quick-btns">
            <el-button size="small" type="primary" plain @click="quickAddStep('click')">点击控件</el-button>
            <el-button size="small" type="primary" plain @click="quickAddStep('input')">文本输入</el-button>
            <el-button size="small" type="primary" plain @click="quickAddStep('swipe')">滑动页面</el-button>
            <el-button size="small" type="success" plain @click="quickAddStep('assert_text')">元素断言</el-button>
            <el-button size="small" type="warning" plain @click="quickAddStep('dismiss_popup')">弹窗关闭</el-button>
          </div>
        </div>
      </div>

      <el-form :model="newStep" label-width="110px" size="small" class="custom-step-form">
        <el-row :gutter="16">
          <el-col :xs="24" :md="8">
            <el-form-item label="步骤类型">
              <el-select v-model="newStep.type" filterable style="width:100%" placeholder="选择或搜索步骤类型">
                <el-option-group label="等待">
                  <el-option label="等待" value="wait" />
                  <el-option label="人工介入等待" value="manual_wait" />
                </el-option-group>
                <el-option-group label="控件操作">
                  <el-option label="点击控件" value="click" />
                  <el-option label="输入文本" value="input" />
                  <el-option label="滑动页面" value="swipe" />
                  <el-option label="OCR 点击" value="tap_ocr" />
                </el-option-group>
                <el-option-group label="应用操作">
                  <el-option label="启动应用" value="launch" />
                  <el-option label="清理缓存" value="clear_cache" />
                  <el-option label="强制停止应用" value="force_stop" />
                  <el-option label="安装包" value="install_apk" />
                  <el-option label="切换 WebView 上下文" value="switch_context" />
                  <el-option label="回收运行时权限" value="revoke_permissions" />
                </el-option-group>
                <el-option-group label="分支判断">
                  <el-option label="分支判断" value="branch" />
                </el-option-group>
                <el-option-group label="循环">
                  <el-option label="循环步骤" value="loop" />
                </el-option-group>
                <el-option-group label="系统操作">
                  <el-option label="系统按键" value="press_key" />
                  <el-option label="设置剪贴板" value="clipboard_set" />
                  <el-option label="亮屏" value="wake_screen" />
                  <el-option label="锁屏" value="lock_screen" />
                </el-option-group>
                <el-option-group label="断言校验">
                  <el-option label="断言文本" value="assert_text" />
                  <el-option label="断言控件存在" value="assert_exists" />
                  <el-option label="OCR 文本断言" value="assert_ocr" />
                  <el-option label="断言 Toast" value="assert_toast" />
                  <el-option label="HTTP 接口断言" value="assert_http" />
                  <el-option label="埋点校验" value="assert_analytics" />
                  <el-option label="组合断言" value="assert_composite" />
                  <el-option label="页面异常检测" value="check_anomaly" />
                  <el-option label="进程存活校验" value="assert_process" />
                  <el-option label="关闭系统弹窗" value="dismiss_popup" />
                  <el-option label="剪贴板断言" value="clipboard_assert" />
                  <el-option label="屏幕状态断言" value="assert_screen" />
                  <el-option label="按键响应断言" value="assert_key" />
                  <el-option label="音量断言" value="assert_volume" />
                  <el-option label="音量变更断言" value="assert_volume_change" />
                  <el-option label="图像相似度断言" value="assert_image" />
                </el-option-group>
                <el-option-group label="专项 / 复用">
                  <el-option label="标记使用账号池" value="use_account_pool" />
                  <el-option label="弱网模拟" value="network_profile" />
                  <el-option label="恢复网络" value="reset_network" />
                  <el-option label="崩溃捕获" value="capture_crash" />
                  <el-option label="切换语言" value="set_locale" />
                  <el-option label="性能采集" value="collect_performance" />
                  <el-option label="冷启动断言" value="assert_cold_start" />
                  <el-option label="调用公共步骤" value="invoke_common" />
                  <el-option label="动态造数" value="data_factory" />
                </el-option-group>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item v-if="newStep.type === 'wait'" label="秒数">
              <el-input-number v-model="newStep.seconds" :min="1" :max="60" style="width:100%" />
            </el-form-item>
            <el-form-item v-else-if="newStep.type === 'branch'" label="判断条件">
              <el-input v-model="newStep.condition" placeholder="例：控件登录按钮存在" />
            </el-form-item>
            <el-form-item v-else-if="newStep.type === 'loop'" label="循环次数">
              <el-input-number v-model="newStep.loop_count" :min="1" :max="100" style="width:100%" />
            </el-form-item>
            <el-form-item v-else label="失败重试次数">
              <el-input-number v-model="newStep.retry_count" :min="0" :max="5" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="失败处理策略">
              <el-select v-model="newStep.on_fail" style="width:100%">
                <el-option label="失败终止" value="fail" />
                <el-option label="失败继续" value="skip" />
                <el-option label="重启应用后重试" value="restart_app" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 各类型参数（保留原能力） -->
        <el-row :gutter="16">
          <el-col :xs="24" :md="8" v-if="['click','input','assert_text','assert_exists','assert_ocr','tap_ocr'].includes(newStep.type)">
            <el-form-item label="控件名">
              <el-input v-model="newStep.element_name" placeholder="login_button" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="['assert_text','assert_ocr','tap_ocr','assert_toast'].includes(newStep.type)">
            <el-form-item label="期望文本">
              <el-input v-model="newStep.expected" placeholder="登录成功" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'input'">
            <el-form-item label="输入文本">
              <el-input v-model="newStep.text" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'launch' || newStep.type === 'install_apk'">
            <el-form-item :label="newStep.type === 'install_apk' ? '安装包路径' : '包名'">
              <el-input
                v-model="newStep.app_package"
                :placeholder="newStep.type === 'install_apk' ? '/path/to/app.apk' : meta.app_package"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'press_key' || newStep.type === 'assert_key'">
            <el-form-item label="按键">
              <el-select v-model="newStep.key" style="width:100%">
                <el-option label="返回" value="back" />
                <el-option label="主页" value="home" />
                <el-option label="多任务" value="recent" />
                <el-option label="电源" value="power" />
                <el-option label="音量+" value="volume_up" />
                <el-option label="音量-" value="volume_down" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'clipboard_set'">
            <el-form-item label="剪贴板文本"><el-input v-model="newStep.text" /></el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'clipboard_assert'">
            <el-form-item label="期望内容"><el-input v-model="newStep.expected" /></el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'assert_screen'">
            <el-form-item label="屏幕状态">
              <el-select v-model="newStep.expected" style="width:100%">
                <el-option label="亮屏" value="on" />
                <el-option label="灭屏" value="off" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'assert_volume'">
            <el-form-item label="期望音量"><el-input-number v-model="newStep.expected" :min="0" :max="15" style="width:100%" /></el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'assert_volume'">
            <el-form-item label="音频流">
              <el-select v-model="newStep.stream" style="width:100%">
                <el-option label="媒体" value="music" />
                <el-option label="铃音" value="ring" />
                <el-option label="闹钟" value="alarm" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'assert_volume'">
            <el-form-item label="容差"><el-input-number v-model="newStep.tolerance" :min="0" :max="3" style="width:100%" /></el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'assert_volume_change'">
            <el-form-item label="变更方向">
              <el-select v-model="newStep.direction" style="width:100%">
                <el-option label="音量增大" value="up" />
                <el-option label="音量减小" value="down" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'set_locale'">
            <el-form-item label="语言">
              <el-select v-model="newStep.locale" style="width:100%">
                <el-option label="简体中文" value="zh_cn" />
                <el-option label="繁体中文" value="zh_tw" />
                <el-option label="English" value="en_us" />
                <el-option label="日本語" value="ja_jp" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'assert_cold_start'">
            <el-form-item label="最大耗时(ms)">
              <el-input-number v-model="newStep.max_ms" :min="500" :max="30000" :step="500" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'network_profile'">
            <el-form-item label="网络档位">
              <el-select v-model="newStep.profile" style="width:100%">
                <el-option label="2G 弱网" value="2g" />
                <el-option label="高延迟" value="high_latency" />
                <el-option label="丢包" value="lossy" />
                <el-option label="断网" value="offline" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'manual_wait'">
            <el-form-item label="提示语">
              <el-input v-model="newStep.prompt" placeholder="请完成验证码输入后点击继续" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'data_factory'">
            <el-form-item label="造数模板">
              <el-select v-model="newStep.template_id" filterable placeholder="选择模板" style="width:100%">
                <el-option v-for="t in dataFactoryTemplates" :key="t.id" :label="t.name" :value="t.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'assert_image'">
            <el-form-item label="模板路径"><el-input v-model="newStep.template_path" /></el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'assert_image'">
            <el-form-item label="相似度阈值">
              <el-input-number v-model="newStep.threshold" :min="0.5" :max="1" :step="0.05" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'invoke_common'">
            <el-form-item label="公共步骤">
              <el-select v-model="newStep.common_step" filterable placeholder="选择公共步骤" style="width:100%">
                <el-option v-for="s in commonSteps" :key="s.id" :label="s.name" :value="s.name" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'invoke_common'">
            <el-form-item label="入参 JSON">
              <el-input v-model="newStep.input_params_json" type="textarea" :rows="2" placeholder='{"username":"test1"}' />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'check_anomaly'">
            <el-form-item label="检测项">
              <el-select v-model="newStep.check_types" style="width:100%">
                <el-option label="全部（黑屏/白屏/闪退）" value="all" />
                <el-option label="仅闪退" value="crash" />
                <el-option label="仅黑屏" value="black" />
                <el-option label="仅白屏" value="white" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'clear_cache'">
            <el-form-item label="清理模式">
              <el-select v-model="newStep.mode" style="width:100%">
                <el-option label="磁盘缓存" value="disk" />
                <el-option label="内存（杀进程）" value="memory" />
                <el-option label="全部" value="all" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'switch_context'">
            <el-form-item label="上下文">
              <el-select v-model="newStep.mode" style="width:100%">
                <el-option label="自动检测" value="auto" />
                <el-option label="原生 Native" value="native" />
                <el-option label="WebView / H5" value="webview" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'assert_http'">
            <el-form-item label="方法">
              <el-select v-model="newStep.method" style="width:100%">
                <el-option label="GET" value="GET" />
                <el-option label="POST" value="POST" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'assert_http'">
            <el-form-item label="URL"><el-input v-model="newStep.url" /></el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'assert_http'">
            <el-form-item label="期望状态码">
              <el-input-number v-model="newStep.expected_status" :min="100" :max="599" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'assert_analytics'">
            <el-form-item label="事件名"><el-input v-model="newStep.event_name" /></el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'assert_composite'">
            <el-form-item label="条件 JSON">
              <el-input v-model="newStep.conditions" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'branch'">
            <el-form-item label="成立分支说明">
              <el-input v-model="newStep.branch_true" placeholder="条件成立时执行的子步骤说明" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'branch'">
            <el-form-item label="否则分支说明">
              <el-input v-model="newStep.branch_false" placeholder="条件不成立时执行的子步骤说明" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8" v-if="newStep.type === 'loop'">
            <el-form-item label="循环体说明">
              <el-input v-model="newStep.loop_body" placeholder="循环内执行的步骤说明" />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="add-actions">
          <el-button type="primary" @click="submitStepForm">
            {{ editingIndex !== null ? '保存步骤修改' : '添加步骤' }}
          </el-button>
          <el-button type="success" plain @click="goElementPicker">拾取控件</el-button>
          <span class="add-tip">一键跳转控件拾取页面，抓取定位信息后可回填到当前步骤</span>
        </div>
      </el-form>
    </AppCard>

    <el-dialog v-model="showPreview" title="生成的执行脚本" width="720px">
      <pre class="preview-code">{{ previewCode }}</pre>
    </el-dialog>

    <el-dialog v-model="showImportCommon" title="导入公共步骤" width="520px">
      <el-select v-model="importCommonNames" multiple filterable placeholder="选择要导入的公共步骤" style="width:100%">
        <el-option v-for="s in commonSteps" :key="s.id" :label="s.name" :value="s.name" />
      </el-select>
      <template #footer>
        <el-button @click="showImportCommon = false">取消</el-button>
        <el-button type="primary" @click="confirmImportCommon">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { taskApi, caseApi, commonStepApi, dataFactoryApi } from '@/api'
import { formatStepTarget, formatStepLocator } from '@/utils/stepDisplay'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const taskId = route.params.id || null
const folderId = route.query.folder_id ? Number(route.query.folder_id) : null
const isAssetMode = route.query.asset === '1' || route.path.startsWith('/cases/')
const saving = ref(false)
const debugging = ref(false)
const showPreview = ref(false)
const previewCode = ref('')
const commonSteps = ref([])
const dataFactoryTemplates = ref([])
const flatFolders = ref([])
const stepKeyword = ref('')
const selectedStepIds = ref(new Set())
const showImportCommon = ref(false)
const importCommonNames = ref([])
const editingIndex = ref(null)
let stepSeq = 1

const meta = reactive({
  name: '',
  platform: 'android',
  app_package: '',
  timeout_seconds: 3600,
  enable_recording: true,
  human_delay: false,
  wait_template: 'standard',
  use_account_pool: false,
  case_status: 'draft',
  folder_id: folderId || null
})

const steps = ref([])

const newStepDefaults = () => ({
  type: 'wait',
  seconds: 2,
  element_name: '',
  text: '',
  app_package: '',
  expected: '',
  x1: 500, y1: 800, x2: 500, y2: 400,
  common_step: '', input_params_json: '', check_types: 'all', mode: 'disk',
  method: 'GET', url: '', expected_status: 200, body_contains: '', timeout: 5,
  event_name: '', props_json: '{}', verify_url: '',
  conditions: '[{"type":"text","value":"首页"}]',
  enabled: true, disable_reason: '', disable_mode: '',
  retry_count: 0, on_fail: 'fail', key: 'back', template_id: null, template_path: '', threshold: 0.85,
  prompt: '', profile: '2g', locale: 'zh_cn', max_ms: 5000,
  stream: 'music', tolerance: 0, direction: 'up',
  condition: '', branch_true: '', branch_false: '', loop_count: 3, loop_body: ''
})

const newStep = reactive(newStepDefaults())

const hasStepSelection = computed(() => selectedStepIds.value.size > 0)

const filteredSteps = computed(() => {
  const k = stepKeyword.value.trim().toLowerCase()
  if (!k) return steps.value
  return steps.value.filter(s => {
    const hay = [
      stepTypeLabel(s.type),
      stepSummary(s),
      stepLocator(s),
      s.element_name,
      s.expected,
      s.common_step,
      s.condition
    ].join(' ').toLowerCase()
    return hay.includes(k)
  })
})

const filteredStepItems = computed(() =>
  filteredSteps.value.map(step => ({
    step,
    index: steps.value.findIndex(s => s.id === step.id)
  })).filter(x => x.index >= 0)
)

const typeLabels = {
  wait: '等待', click: '点击', tap_xy: '坐标点击', input: '输入', launch: '启动', swipe: '滑动',
  assert_text: '断言', assert_exists: '断言存在', invoke_common: '公共步骤',
  check_anomaly: '异常检测', assert_process: '进程校验', clear_cache: '清理缓存', force_stop: '强制停止',
  assert_toast: 'Toast断言', assert_http: 'HTTP断言', assert_analytics: '埋点校验', assert_composite: '组合断言', dismiss_popup: '关弹窗',
  switch_context: '切换上下文', revoke_permissions: '权限回收',
  assert_ocr: 'OCR断言', tap_ocr: 'OCR点击',
  press_key: '系统按键', clipboard_set: '剪贴板', clipboard_assert: '剪贴板断言',
  wake_screen: '亮屏', lock_screen: '锁屏', assert_screen: '屏幕断言', assert_key: '按键断言',
  assert_volume: '音量断言', assert_volume_change: '音量变更断言',
  assert_image: '图像断言', data_factory: '动态造数', use_account_pool: '账号池',
  manual_wait: '人工介入', network_profile: '弱网模拟', reset_network: '恢复网络', capture_crash: '崩溃捕获',
  set_locale: '切换语言', collect_performance: '性能采集', assert_cold_start: '冷启动',
  install_apk: '安装包', branch: '分支判断', loop: '循环'
}

const WAIT_TYPES = new Set(['wait', 'manual_wait'])
const CLICK_TYPES = new Set(['click', 'tap_xy', 'long_press', 'tap_ocr', 'input', 'swipe'])
const ASSERT_TYPES = new Set([
  'assert_text', 'assert_exists', 'assert_ocr', 'assert_toast', 'assert_http', 'assert_analytics',
  'assert_composite', 'check_anomaly', 'assert_process', 'clipboard_assert', 'assert_screen',
  'assert_key', 'assert_volume', 'assert_volume_change', 'assert_image', 'assert_cold_start'
])
const APP_TYPES = new Set([
  'launch', 'clear_cache', 'force_stop', 'install_apk', 'switch_context', 'revoke_permissions',
  'press_key', 'clipboard_set', 'wake_screen', 'lock_screen', 'dismiss_popup'
])

function stepTypeLabel(t) { return typeLabels[t] || t }

function stepToneClass(type) {
  if (WAIT_TYPES.has(type)) return 'tone-wait'
  if (CLICK_TYPES.has(type)) return 'tone-click'
  if (ASSERT_TYPES.has(type)) return 'tone-assert'
  if (APP_TYPES.has(type) || type === 'branch' || type === 'loop') return 'tone-app'
  return 'tone-default'
}

function stepTagType(type) {
  if (WAIT_TYPES.has(type)) return 'info'
  if (CLICK_TYPES.has(type)) return 'primary'
  if (ASSERT_TYPES.has(type)) return 'success'
  if (APP_TYPES.has(type)) return 'warning'
  if (type === 'branch' || type === 'loop') return 'warning'
  return ''
}

function stepLocator(step) {
  return formatStepLocator(step)
}

function stepSummary(step) {
  const target = formatStepTarget(step)
  switch (step.type) {
    case 'wait': return `${step.seconds}s`
    case 'click':
    case 'tap_xy':
    case 'long_press':
    case 'tap_ocr':
      return target || stepTypeLabel(step.type)
    case 'input': return step.text
    case 'launch': return step.app_package || meta.app_package
    case 'install_apk': return step.app_package || '安装包'
    case 'swipe': return target || `${step.x1},${step.y1}→${step.x2},${step.y2}`
    case 'assert_text': return `${step.element_name || ''} = ${step.expected}`
    case 'invoke_common': return step.common_step || '(未选)'
    case 'check_anomaly': return step.check_types || 'all'
    case 'assert_process': return meta.app_package || '当前应用'
    case 'clear_cache': return { disk: '磁盘缓存', memory: '内存', all: '全部' }[step.mode] || step.mode
    case 'force_stop': return meta.app_package || '当前应用'
    case 'assert_toast': return step.expected
    case 'assert_http': return `${step.method || 'GET'} ${step.url}`
    case 'assert_analytics': return step.event_name || '(未填事件)'
    case 'assert_composite': return '多条件'
    case 'dismiss_popup': return '系统弹窗'
    case 'switch_context': return { auto: '自动', native: 'Native', webview: 'WebView' }[step.mode] || step.mode
    case 'revoke_permissions': return 'pm revoke'
    case 'assert_ocr': return step.expected
    case 'press_key': case 'assert_key': return step.key || 'back'
    case 'clipboard_set': return step.text
    case 'clipboard_assert': return step.expected
    case 'wake_screen': return '唤醒'
    case 'lock_screen': return '锁屏'
    case 'assert_screen': return step.expected || 'on'
    case 'assert_volume': return `${step.stream || 'music'}=${step.expected ?? 0}`
    case 'assert_volume_change': return `${step.direction || 'up'} ${step.key || 'auto'}`
    case 'assert_image': return `${step.template_path} ≥${step.threshold || 0.85}`
    case 'data_factory': return dataFactoryTemplates.value.find(t => t.id === step.template_id)?.name || `#${step.template_id}`
    case 'manual_wait': return step.prompt || '等待人工处理'
    case 'network_profile': return step.profile || '2g'
    case 'reset_network': return '恢复正常'
    case 'capture_crash': return '抓取崩溃日志'
    case 'set_locale': return step.locale || 'zh_cn'
    case 'collect_performance': return '内存/性能'
    case 'assert_cold_start': return `≤${step.max_ms || 5000}ms`
    case 'branch': return step.condition || '条件分支'
    case 'loop': return `循环 ${step.loop_count || 1} 次`
    default: return target || step.element_name || step.expected || stepTypeLabel(step.type)
  }
}

function flattenFolders(nodes, prefix = '') {
  const list = []
  for (const node of nodes || []) {
    if (!node.id) continue
    const label = prefix ? `${prefix} / ${node.name}` : node.name
    list.push({ id: node.id, label })
    if (node.children?.length) list.push(...flattenFolders(node.children, label))
  }
  return list
}

async function loadFolders() {
  try {
    const res = await caseApi.folderTree()
    flatFolders.value = flattenFolders(res.data || [])
  } catch {
    flatFolders.value = []
  }
}

function resetNewStep(type = 'wait') {
  Object.assign(newStep, newStepDefaults(), { type })
}

function toggleStepSelect(id, checked) {
  const next = new Set(selectedStepIds.value)
  if (checked) next.add(id)
  else next.delete(id)
  selectedStepIds.value = next
}

function quickAddStep(type, extra = {}) {
  const step = {
    id: stepSeq++,
    enabled: true,
    disable_reason: '',
    disable_mode: '',
    ...newStepDefaults(),
    type,
    ...extra
  }
  steps.value.push(step)
  ElMessage.success(`已添加「${stepTypeLabel(type)}」`)
}

function buildStepFromForm() {
  const step = { id: stepSeq++, enabled: true, disable_reason: '', disable_mode: '', ...JSON.parse(JSON.stringify(newStep)) }
  if (step.type === 'invoke_common' && step.input_params_json) {
    try {
      step.input_params = JSON.parse(step.input_params_json)
    } catch {
      ElMessage.warning('入参 JSON 格式错误')
      return null
    }
  }
  delete step.input_params_json
  if (step.type === 'clear_cache' && !step.mode) step.mode = 'disk'
  return step
}

function submitStepForm() {
  if (editingIndex.value !== null) {
    const step = buildStepFromForm()
    if (!step) return
    const id = steps.value[editingIndex.value]?.id
    step.id = id ?? step.id
    steps.value[editingIndex.value] = step
    editingIndex.value = null
    resetNewStep()
    ElMessage.success('步骤已更新')
    return
  }
  addStep()
}

function addStep() {
  const step = buildStepFromForm()
  if (!step) return
  steps.value.push(step)
  ElMessage.success('步骤已添加')
}

function editStep(idx) {
  const step = steps.value[idx]
  if (!step) return
  editingIndex.value = idx
  Object.assign(newStep, newStepDefaults(), JSON.parse(JSON.stringify(step)), {
    input_params_json: step.input_params ? JSON.stringify(step.input_params, null, 2) : (step.input_params_json || '')
  })
  nextTick(() => {
    document.querySelector('.add-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

function cancelEdit() {
  editingIndex.value = null
  resetNewStep()
}

function copyStep(idx) {
  const src = steps.value[idx]
  if (!src) return
  const cloned = { ...JSON.parse(JSON.stringify(src)), id: stepSeq++ }
  steps.value.splice(idx + 1, 0, cloned)
  ElMessage.success('步骤已复制')
}

function batchCopySteps() {
  const ids = selectedStepIds.value
  const copies = steps.value
    .filter(s => ids.has(s.id))
    .map(s => ({ ...JSON.parse(JSON.stringify(s)), id: stepSeq++ }))
  steps.value.push(...copies)
  selectedStepIds.value = new Set()
  ElMessage.success(`已复制 ${copies.length} 个步骤`)
}

async function batchDeleteSteps() {
  const n = selectedStepIds.value.size
  await ElMessageBox.confirm(`确定删除选中的 ${n} 个步骤？`, '批量删除', { type: 'warning' })
  steps.value = steps.value.filter(s => !selectedStepIds.value.has(s.id))
  selectedStepIds.value = new Set()
  ElMessage.success('已删除')
}

function removeStep(idx) {
  const id = steps.value[idx]?.id
  steps.value.splice(idx, 1)
  if (id != null) {
    const next = new Set(selectedStepIds.value)
    next.delete(id)
    selectedStepIds.value = next
  }
  if (editingIndex.value === idx) cancelEdit()
  else if (editingIndex.value !== null && editingIndex.value > idx) editingIndex.value -= 1
}

function moveStep(idx, delta) {
  const target = idx + delta
  if (target < 0 || target >= steps.value.length) return
  const arr = steps.value
  ;[arr[idx], arr[target]] = [arr[target], arr[idx]]
  if (editingIndex.value === idx) editingIndex.value = target
  else if (editingIndex.value === target) editingIndex.value = idx
}

function addBranchStep() {
  quickAddStep('branch', { condition: '控件存在', branch_true: '执行成立分支', branch_false: '执行否则分支' })
}

function addLoopStep() {
  quickAddStep('loop', { loop_count: 3, loop_body: '循环体步骤' })
}

function confirmImportCommon() {
  if (!importCommonNames.value.length) {
    ElMessage.warning('请选择公共步骤')
    return
  }
  for (const name of importCommonNames.value) {
    quickAddStep('invoke_common', { common_step: name })
  }
  importCommonNames.value = []
  showImportCommon.value = false
}

function focusAddPanel(type = 'wait') {
  resetNewStep(type)
  nextTick(() => {
    document.querySelector('.add-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

function goDevicePool() {
  router.push('/devices')
}

function goElementPicker() {
  const q = {}
  if (meta.app_package) q.package = meta.app_package
  router.push({ path: '/element-picker', query: q })
}

function onDebugCommand(cmd) {
  debugRun(cmd)
}

function onSaveCommand(cmd) {
  if (cmd === 'template') saveAsTemplate()
  else saveTask(cmd)
}

function buildVisualJson() {
  const payload = {
    version: 1,
    human_delay: meta.human_delay,
    steps: steps.value.map(({ id, ...rest }) => rest)
  }
  if (meta.wait_template) payload.wait_template = meta.wait_template
  return JSON.stringify(payload)
}

function buildCasePayload() {
  const payload = {
    name: meta.name,
    platform: meta.platform,
    script_type: 'visual',
    steps_content: buildVisualJson(),
    app_package: meta.app_package,
    timeout_seconds: meta.timeout_seconds,
    enable_recording: meta.enable_recording,
    case_status: meta.case_status
  }
  const fid = meta.folder_id ?? folderId
  if (fid) payload.folder_id = fid
  else payload.folder_id = null
  return payload
}

function buildPayload() {
  return {
    name: meta.name,
    platform: meta.platform,
    script_type: 'visual',
    script_content: buildVisualJson(),
    app_package: meta.app_package,
    timeout_seconds: meta.timeout_seconds,
    enable_recording: meta.enable_recording,
    parallel_count: 1,
    use_account_pool: meta.use_account_pool
  }
}

async function debugRun(mode = 'full') {
  if (!meta.name) {
    ElMessage.warning('请填写用例名称')
    return
  }
  if (!steps.value.length) {
    ElMessage.warning('请至少添加一个步骤')
    return
  }
  debugging.value = true
  try {
    let caseId = taskId
    if (isAssetMode) {
      if (caseId) {
        await caseApi.update(caseId, buildCasePayload())
      } else {
        const res = await caseApi.create(buildCasePayload())
        caseId = res.data.id
        router.replace(`/cases/editor/${caseId}?asset=1`)
      }
      const runRes = await caseApi.run(caseId)
      ElMessage.success(`调试任务 #${runRes.data.id} 已提交`)
      const query = { taskId: runRes.data.id }
      if (mode === 'step') query.mode = 'step'
      router.push({ path: `/cases/${caseId}/debug`, query })
    } else {
      ElMessage.info('请先保存为用例资产后再调试执行')
    }
  } finally {
    debugging.value = false
  }
}

async function saveTask(mode = 'keep') {
  if (!meta.name?.trim()) {
    ElMessage.warning('请填写用例名称')
    return
  }
  if (mode === 'draft') meta.case_status = 'draft'
  if (mode === 'active') meta.case_status = 'active'
  saving.value = true
  try {
    if (isAssetMode) {
      if (taskId) {
        await caseApi.update(taskId, buildCasePayload())
        ElMessage.success(mode === 'active' ? '已提交生效' : mode === 'draft' ? '草稿已保存' : '用例已更新')
      } else {
        const res = await caseApi.create(buildCasePayload())
        ElMessage.success(mode === 'active' ? '已创建并提交生效' : '用例已创建')
        router.replace(`/cases/editor/${res.data.id}?asset=1`)
      }
    } else if (taskId) {
      await taskApi.update(taskId, buildPayload())
      ElMessage.success('任务已更新')
    } else {
      const res = await taskApi.create(buildPayload())
      ElMessage.success('任务已创建')
      router.replace(`/cases/editor/${res.data.id}`)
    }
  } finally {
    saving.value = false
  }
}

async function saveAsTemplate() {
  if (!meta.name?.trim()) {
    ElMessage.warning('请先填写用例名称')
    return
  }
  const { value } = await ElMessageBox.prompt('请输入模板名称', '另存为模板', {
    inputValue: `${meta.name}_模板`,
    confirmButtonText: '保存模板',
    cancelButtonText: '取消'
  }).catch(() => ({ value: null }))
  if (!value) return
  saving.value = true
  try {
    const payload = {
      ...buildCasePayload(),
      name: value.trim(),
      case_status: 'draft',
      folder_id: meta.folder_id || null
    }
    await caseApi.create(payload)
    ElMessage.success('模板已保存，可在用例列表中继续编辑')
  } finally {
    saving.value = false
  }
}

async function previewScript() {
  const res = await taskApi.previewVisual(buildVisualJson())
  previewCode.value = res.data.script
  showPreview.value = true
}

async function loadTask() {
  if (!taskId) return
  if (isAssetMode) {
    const res = await caseApi.get(taskId)
    const c = res.data
    meta.name = c.name
    meta.platform = c.platform
    meta.app_package = c.app_package || ''
    meta.timeout_seconds = c.timeout_seconds
    meta.enable_recording = c.enable_recording
    meta.case_status = c.case_status || 'draft'
    meta.folder_id = c.folder_id || null
    try {
      const parsed = JSON.parse(c.steps_content)
      meta.human_delay = parsed.human_delay || false
      meta.wait_template = parsed.wait_template || 'standard'
      steps.value = (parsed.steps || []).map(s => ({ id: stepSeq++, enabled: s.enabled !== false, disable_reason: s.disable_reason || '', ...s }))
    } catch { steps.value = [] }
    return
  }
  const res = await taskApi.get(taskId)
  const task = res.data
  meta.name = task.name
  meta.platform = task.platform
  meta.app_package = task.app_package || ''
  meta.timeout_seconds = task.timeout_seconds
  meta.enable_recording = task.enable_recording
  meta.use_account_pool = task.use_account_pool || false
  try {
    const parsed = JSON.parse(task.script_content)
    meta.human_delay = parsed.human_delay || false
    meta.wait_template = parsed.wait_template || 'standard'
    steps.value = (parsed.steps || []).map(s => ({ id: stepSeq++, enabled: s.enabled !== false, ...s }))
  } catch {
    steps.value = []
  }
}

onMounted(async () => {
  loadFolders()
  try { commonSteps.value = (await commonStepApi.list()).data } catch { /* ignore */ }
  try { dataFactoryTemplates.value = (await dataFactoryApi.listTemplates()).data } catch { /* ignore */ }
  await loadTask()
})
</script>

<script>
/** 字段悬浮释义：用自定义指令避免每个 label 包一层 Tooltip 组件 */
export default {
  directives: {
    tooltip: {
      mounted(el, binding) {
        el.setAttribute('title', binding.value || '')
        el.style.cursor = 'help'
        el.style.borderBottom = '1px dashed rgba(100,116,139,0.45)'
      },
      updated(el, binding) {
        el.setAttribute('title', binding.value || '')
      }
    }
  }
}
</script>

<style scoped>
.header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
  align-items: center;
}
.btn-secondary {
  --el-button-bg-color: #f1f5f9;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #475569;
  --el-button-hover-bg-color: #e2e8f0;
  --el-button-hover-border-color: #cbd5e1;
}

.main-row {
  margin-bottom: 16px;
  align-items: stretch;
}
.main-row > .el-col {
  display: flex;
  flex-direction: column;
}
.meta-card,
.steps-card {
  flex: 1;
  width: 100%;
  display: flex !important;
  flex-direction: column;
  height: 100%;
  margin-bottom: 0 !important;
}
.meta-card :deep(.el-card__header),
.steps-card :deep(.el-card__header) {
  flex-shrink: 0;
}
.meta-card :deep(.el-card__body),
.steps-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.steps-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.steps-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-shrink: 0;
}
.steps-count { font-size: 12px; color: var(--atp-text-secondary); }

.steps-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}

.steps-empty {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 24px 16px;
}

.form-group { margin-bottom: 8px; }
.form-group--advanced {
  margin-top: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--atp-border-light, #eef2f7);
}
.form-group-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--atp-text);
  margin-bottom: 12px;
}
.pkg-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
.pkg-row .el-input { flex: 1; }
.switch-with-hint {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.field-hint {
  font-size: 12px;
  color: var(--atp-text-secondary);
  line-height: 1.4;
}

.steps-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
  width: 100%;
}
.steps-header-left { display: flex; flex-direction: column; gap: 2px; }
.steps-title { font-weight: 600; }
.steps-hint { font-size: 12px; color: var(--atp-text-secondary); }
.steps-header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: flex-end;
}
.empty-title {
  margin: 0 0 16px;
  font-size: 15px;
  color: var(--atp-text-secondary);
}
.empty-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}

.step-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 10px;
  margin-bottom: 8px;
  border-radius: 10px;
  border: 1px solid transparent;
  transition: box-shadow 0.15s, transform 0.15s;
}
.step-item:hover { box-shadow: 0 4px 14px rgba(15, 23, 42, 0.06); }
.step-item.disabled { opacity: 0.55; }
.step-item.tone-wait { background: #f8fafc; border-color: #e2e8f0; }
.step-item.tone-click { background: #eff6ff; border-color: #bfdbfe; }
.step-item.tone-assert { background: #ecfdf5; border-color: #a7f3d0; }
.step-item.tone-app { background: #fff7ed; border-color: #fed7aa; }
.step-item.tone-default { background: #f8fafc; border-color: #e2e8f0; }

.step-index {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--atp-primary), var(--atp-brand-400, #38bdf8));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}
.step-body {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}
.step-desc {
  color: var(--atp-text-secondary);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.step-locator {
  color: var(--el-color-info);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.step-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  flex-shrink: 0;
}
.btn-copy {
  --el-button-bg-color: #f1f5f9;
  --el-button-border-color: #e2e8f0;
  --el-button-text-color: #64748b;
}

.add-panel { margin-top: 4px; }
.add-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.quick-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 14px;
  border-bottom: 1px dashed var(--atp-border-light, #e2e8f0);
}
.quick-group {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  flex-wrap: wrap;
}
.quick-label {
  flex-shrink: 0;
  width: 88px;
  font-size: 12px;
  font-weight: 600;
  color: var(--atp-text-secondary);
  line-height: 28px;
}
.quick-btns {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  flex: 1;
}
.add-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 8px;
}
.add-tip {
  font-size: 12px;
  color: #94a3b8;
}

.preview-code {
  background: var(--atp-code-bg);
  color: var(--atp-screen-text);
  padding: 20px;
  border-radius: var(--atp-radius-md, 12px);
  max-height: 480px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
  margin: 0;
}

@media (max-width: 992px) {
  .steps-header-actions { justify-content: flex-start; }
  .quick-label { width: 100%; }
}
</style>
