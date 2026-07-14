export const APP_NAME = 'TestFlow'
export const APP_TAGLINE = '移动端自动化测试管理系统'
export const APP_TITLE = `${APP_NAME} - ${APP_TAGLINE}`

export function pageTitle(section) {
  return section ? `${section} - ${APP_NAME}` : APP_TITLE
}
