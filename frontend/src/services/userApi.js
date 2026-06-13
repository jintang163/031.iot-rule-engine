import request from '../utils/request'

export const getUserList = (params) => {
  return request({
    url: '/user/list',
    method: 'get',
    params
  })
}

export const getUser = (id) => {
  return request({
    url: `/user/${id}`,
    method: 'get'
  })
}

export const createUser = (data) => {
  return request({
    url: '/user',
    method: 'post',
    data
  })
}

export const updateUser = (data) => {
  return request({
    url: '/user',
    method: 'put',
    data
  })
}

export const deleteUser = (id) => {
  return request({
    url: `/user/${id}`,
    method: 'delete'
  })
}
