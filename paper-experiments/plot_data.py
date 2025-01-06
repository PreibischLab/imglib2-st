import os

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt


NAMES = ['automatic', 'SP', 'NK', 'DLP', 'MI']
data = []
colors = []

# load pairwise error data
for name1 in NAMES:
    for name2, color in zip(NAMES, ['tab:grey', 'tab:blue', 'tab:green', 'tab:red', 'tab:purple']):
        if name1 == name2:
            continue

        script_dir = os.path.dirname(__file__)
        file_path = os.path.join(script_dir, f'compare-{name1}-{name2}.csv')
        
        if not os.path.exists(file_path):
            file_path = os.path.join(script_dir, f'compare-{name2}-{name1}.csv')

        file_name = file_path
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
for label, color in zip(xtick_labels, ['tab:grey', 'tab:blue', 'tab:green', 'tab:red', 'tab:purple']):
    label.set_color(color)

plt.savefig('comparison.pdf')
# plt.show()
