import os

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt


NAMES = ['automatic', 'SP', 'NK', 'DLP', 'MI']
COLORS = ['tab:grey', 'tab:blue', 'tab:green', 'tab:red', 'tab:purple']
script_dir = os.path.dirname(__file__)

data = []
colors = []

# load pairwise error data
# make sure to 
for name1 in NAMES:
    for name2, color in zip(NAMES, COLORS):
        if name1 == name2:
            continue

        file_name = os.path.join(script_dir, f'compare-{name1}-{name2}.csv')
        df = pd.read_csv(file_name)

        colors.append(color)
        data.append(df['meanError'])

# plot pairwise error data as grouped boxplots
x = np.array([1, 2, 3, 4, 11, 12, 13, 14, 21, 22, 23, 24, 31, 32, 33, 34, 41, 42, 43, 44])
y = np.array(data).T
boxes = plt.boxplot(y, positions=x, patch_artist=True)

for patch, color in zip(boxes['boxes'], colors):
    patch.set_facecolor(color)

plt.ylabel('Pairwise error [px]')
plt.xlim(-2, 47)
plt.xticks([2.5, 12.5, 22.5, 32.5, 42.5], ['STIM', 'Human #1', 'Human #2', 'Human #3', 'Human #4'])
xtick_labels = plt.gca().get_xticklabels()
for label, color in zip(xtick_labels, COLORS):
    label.set_color(color)

plt.savefig('pairwise_error.pdf')
# plt.show()


# load parameter scan data
file_name = os.path.join(script_dir, 'comparison-parameter-scan.csv')
df = pd.read_csv(file_name)

# plot parameter scan data in three groups of 6
plt.figure()

# Split the data into three groups
GROUP_SIZE = 6
NUM_GROUPS = len(df) // GROUP_SIZE
SPACING = 2  # space between groups
for i in range(NUM_GROUPS):
    start_idx = i * GROUP_SIZE
    end_idx = start_idx + GROUP_SIZE
    x_values = np.arange(start_idx, end_idx) + i * SPACING
    plt.plot(
        x_values,
        df['mean'][start_idx:end_idx],
        marker='o',
        label=f'Scale {df["scale"][start_idx]}'
    )

# set xticks to be only the 'renderFactor'
xticks_labels = [f"{renderFactor}" for renderFactor in df['renderFactor']]
plt.xticks(ticks=np.arange(len(df)) + (np.arange(len(df)) // GROUP_SIZE) * SPACING, labels=xticks_labels, rotation=45, ha='right')

plt.xlabel('Render Factor')
plt.ylabel('Distance to Human #1 [px]')
plt.legend()
plt.tight_layout()
plt.savefig('parameter_scan.pdf')
# plt.show()
