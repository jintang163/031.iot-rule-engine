import request from '../utils/request'

export const getRoleList = (params) => {
  return request({
    url: '/role/list',
    method: 'get',
    params
  })
}

export const getAllRoles = () => {
  return request({
    url: '/role/all',
    method: 'get'
  })
}

export const getPermissionTree = () => {
  return request({
    url: '/role/permissions/tree',
    method: 'get'
  })
}

export const getRolePermissions = (roleId) => {
  return request({
    url: `/role/${roleId}/permissions`,
    method: 'get'
  })
}

export const createRole = (data) => {
  return request({
    url: '/role',
    method: 'post',
    data
  })
}

export const updateRole = (data) => {
  return request({
    url: '/role',
    method: 'put',
    data
  })
}

export const assignPermissions = (roleId, permissionIds) => {
  return request({
    url: `/role/${roleId}/assign-permissions`,
    method: 'post',
    data: { permissionIds }
  })
}

export const deleteRole = (id) => {
  return request({
    url: `/role/${id}`,
    method: 'delete'
  })
}
