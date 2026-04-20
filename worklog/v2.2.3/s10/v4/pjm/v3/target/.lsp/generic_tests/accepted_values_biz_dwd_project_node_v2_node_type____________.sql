{{ config({"severity":"Warn","tags":[]}) }}
{{ test_accepted_values(column_name="node_type", model=get_where_subquery(ref('biz_dwd_project_node_v2')), values=["一般节点","重要节点","重大节点","里程碑节点"]) }}