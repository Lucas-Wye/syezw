import pandas as pd
import geopandas as gpd
import matplotlib.pyplot as plt
import contextily as ctx
from shapely.geometry import LineString

# ------------------------------
# 1. 读取 CSV 数据
# ------------------------------
df = pd.read_csv('gps.csv')

# 将 timestamp 列转为 datetime 类型，便于排序和颜色映射
df['timestamp'] = pd.to_datetime(df['timestamp'])

# 按时间排序（确保路线顺序正确）
df = df.sort_values('timestamp')

# ------------------------------
# 2. 创建 GeoDataFrame（点）
# ------------------------------
gdf_points = gpd.GeoDataFrame(
    df,
    geometry=gpd.points_from_xy(df['longitude'], df['latitude']),
    crs="EPSG:4326"          # WGS84 经纬度坐标系
)

# ------------------------------
# 3. 由点生成路线（LineString）
# ------------------------------
# 将所有的点按顺序连成一条线
line = LineString(gdf_points.geometry.tolist())
gdf_line = gpd.GeoDataFrame(geometry=[line], crs=gdf_points.crs)

# ------------------------------
# 4. 绘制基础地图 + 街道底图 + 时间颜色映射
# ------------------------------
# 将数据转换为 Web Mercator 投影（EPSG:3857），以便与底图匹配
gdf_points_mercator = gdf_points.to_crs(epsg=3857)
gdf_line_mercator = gdf_line.to_crs(epsg=3857)

# 创建画布
fig, ax = plt.subplots(figsize=(12, 10))

# 绘制路线（灰色线）
gdf_line_mercator.plot(ax=ax, color='gray', linewidth=2.5, alpha=0.7, label='route')

# 用颜色映射展示时间变化
# 将时间转换为数值（Unix 时间戳）用于颜色映射
time_numeric = gdf_points['timestamp'].astype('int64') // 10**9  # 秒为单位
norm = plt.Normalize(time_numeric.min(), time_numeric.max())
cmap = plt.cm.plasma

# 绘制散点图，颜色随时间变化
sc = ax.scatter(
    gdf_points_mercator.geometry.x,
    gdf_points_mercator.geometry.y,
    c=time_numeric,
    cmap=cmap,
    norm=norm,
    s=60,
    edgecolor='black',
    linewidth=0.5,
    label='tracing point'
)

# 添加颜色条（显示时间）
cbar = plt.colorbar(sc, ax=ax, shrink=0.6, label='time')
# 可选：将颜色条刻度显示为可读时间格式
# 此处简化处理，不展开

# 添加街道底图
ctx.add_basemap(ax, source=ctx.providers.OpenStreetMap.Mapnik, alpha=0.8)

# 设置标题和隐藏坐标轴
ax.set_title('GPS Rounting', fontsize=14)
ax.set_axis_off()

# 添加图例
ax.legend(loc='upper right')

# 显示图形
plt.tight_layout()
plt.show()
