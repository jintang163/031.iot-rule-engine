import request from '../utils/request'

export const getTenantList = (params) => {
  return request({
    url: '/tenant/list',
    method: 'get',
    params
  })
}

export const getTenant = (id) => {
  return request({
    url: `/tenant/${id}`,
    method: 'get'
  })
}

export const createTenant = (data) => {
  return request({
    url: '/tenant',
    method: 'post',
    data
  })
}

export const updateTenant = (data) => {
  return request({
    url: '/tenant',
    method: 'put',
    data
  })
}

export const deleteTenant = (id) => {
  return request({
    url: `/tenant/${id}`,
    method: 'delete'
  })
}
