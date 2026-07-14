package middleware

import (
	"net/http"
	"strings"
	"time"

	"github.com/atp-platform/backend/internal/model"
	"github.com/atp-platform/backend/internal/pkg/response"
	"github.com/atp-platform/backend/internal/service"
	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
)

type Claims struct {
	UserID   uint64         `json:"user_id"`
	Username string         `json:"username"`
	Role     model.UserRole `json:"role"`
	jwt.RegisteredClaims
}

type AuthMiddleware struct {
	authService *service.AuthService
	jwtSecret   []byte
}

func NewAuthMiddleware(authService *service.AuthService, jwtSecret string) *AuthMiddleware {
	return &AuthMiddleware{authService: authService, jwtSecret: []byte(jwtSecret)}
}

func (m *AuthMiddleware) GenerateToken(user *model.User) (string, time.Time, error) {
	expiresAt := time.Now().Add(24 * time.Hour)
	claims := Claims{
		UserID:   user.ID,
		Username: user.Username,
		Role:     user.Role,
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(expiresAt),
			IssuedAt:  jwt.NewNumericDate(time.Now()),
			Subject:   user.Username,
		},
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	signed, err := token.SignedString(m.jwtSecret)
	return signed, expiresAt, err
}

func (m *AuthMiddleware) JWTAuth() gin.HandlerFunc {
	return func(c *gin.Context) {
		authHeader := c.GetHeader("Authorization")
		if authHeader == "" {
			response.Unauthorized(c, "缺少认证令牌")
			c.Abort()
			return
		}
		parts := strings.SplitN(authHeader, " ", 2)
		if len(parts) != 2 || parts[0] != "Bearer" {
			response.Unauthorized(c, "认证令牌格式错误")
			c.Abort()
			return
		}

		token, err := jwt.ParseWithClaims(parts[1], &Claims{}, func(t *jwt.Token) (interface{}, error) {
			return m.jwtSecret, nil
		})
		if err != nil || !token.Valid {
			response.Unauthorized(c, "认证令牌无效或已过期")
			c.Abort()
			return
		}

		claims, ok := token.Claims.(*Claims)
		if !ok {
			response.Unauthorized(c, "认证令牌解析失败")
			c.Abort()
			return
		}

		c.Set("user_id", claims.UserID)
		c.Set("username", claims.Username)
		c.Set("role", claims.Role)
		c.Next()
	}
}

func (m *AuthMiddleware) RequireRoles(roles ...model.UserRole) gin.HandlerFunc {
	roleSet := make(map[model.UserRole]bool)
	for _, r := range roles {
		roleSet[r] = true
	}
	return func(c *gin.Context) {
		role, exists := c.Get("role")
		if !exists {
			response.Forbidden(c, "权限不足")
			c.Abort()
			return
		}
		if !roleSet[role.(model.UserRole)] {
			response.Forbidden(c, "权限不足")
			c.Abort()
			return
		}
		c.Next()
	}
}

func GetUserID(c *gin.Context) uint64 {
	if v, ok := c.Get("user_id"); ok {
		return v.(uint64)
	}
	return 0
}

func GetRole(c *gin.Context) model.UserRole {
	if v, ok := c.Get("role"); ok {
		return v.(model.UserRole)
	}
	return ""
}

func CanEdit(role model.UserRole) bool {
	return role == model.RoleSuperAdmin || role == model.RoleTestAdmin || role == model.RoleTester
}

func AuditLog(c *gin.Context) {
	c.Next()
	if c.Writer.Status() >= http.StatusBadRequest {
		return
	}
	// 审计日志由 handler 层显式调用
}
