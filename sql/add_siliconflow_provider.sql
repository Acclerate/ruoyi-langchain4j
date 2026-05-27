-- 添加SiliconFlow提供商到字典数据
INSERT INTO sys_dict_data (dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default,
                           status, create_by, create_time, update_by, update_time, remark)
VALUES (0, 'SiliconFlow', 'SiliconFlow', 'ai_model_provider', NULL, 'warning', 'N', '0', 'admin', NOW(), '', NULL, NULL);

-- 验证插入结果
SELECT * FROM sys_dict_data WHERE dict_type = 'ai_model_provider' ORDER BY dict_sort;
