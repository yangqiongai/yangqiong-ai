/*
 * Copyright (C) 2026 yangqiong
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3 of the License
 * only ("AGPL-3.0-only") and not any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.yangqiongai.ai.platform.system.dto;

import com.yangqiongai.ai.platform.system.entity.SystemMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点
 * @author yangqiong
 */
public class SystemMenuTreeNode {

    /**
     * 菜单ID
     */
    private Long id;

    /**
     * 语义键
     */
    private String menuKey;

    /**
     * 端标识
     */
    private String appCode;

    /**
     * 父菜单ID
     */
    private Long parentId;

    /**
     * 类型：GROUP/PAGE/LINK
     */
    private String menuType;

    /**
     * 菜单名称
     */
    private String name;

    /**
     * 前端路由路径
     */
    private String path;

    /**
     * 图标名字符串
     */
    private String icon;

    /**
     * 同级显示顺序
     */
    private Integer sortOrder;

    /**
     * 全局显隐：1显示/0隐藏
     */
    private Integer visible;

    /**
     * 状态：1启用/0停用
     */
    private Integer status;

    /**
     * 权限点编码
     */
    private String permissionCode;

    /**
     * 配置开关键
     */
    private String featureKey;

    /**
     * 子菜单
     */
    private List<SystemMenuTreeNode> children = new ArrayList<>();

    public SystemMenuTreeNode() {
    }

    public SystemMenuTreeNode(SystemMenu menu) {
        this.id = menu.getId();
        this.menuKey = menu.getMenuKey();
        this.appCode = menu.getAppCode();
        this.parentId = menu.getParentId();
        this.menuType = menu.getMenuType();
        this.name = menu.getName();
        this.path = menu.getPath();
        this.icon = menu.getIcon();
        this.sortOrder = menu.getSortOrder();
        this.visible = menu.getVisible();
        this.status = menu.getStatus();
        this.permissionCode = menu.getPermissionCode();
        this.featureKey = menu.getFeatureKey();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMenuKey() {
        return menuKey;
    }

    public void setMenuKey(String menuKey) {
        this.menuKey = menuKey;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getMenuType() {
        return menuType;
    }

    public void setMenuType(String menuType) {
        this.menuType = menuType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Integer getVisible() {
        return visible;
    }

    public void setVisible(Integer visible) {
        this.visible = visible;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
    }

    public String getFeatureKey() {
        return featureKey;
    }

    public void setFeatureKey(String featureKey) {
        this.featureKey = featureKey;
    }

    public List<SystemMenuTreeNode> getChildren() {
        return children;
    }

    public void setChildren(List<SystemMenuTreeNode> children) {
        this.children = children;
    }
}
