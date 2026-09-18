import math
import torch
from torch import nn

class AttentionBlock(nn.Module):
    def __init__(self, width=64, heads=4):
        super().__init__()
        self.heads, self.width = heads, width
        self.norm1, self.norm2 = nn.LayerNorm(width), nn.LayerNorm(width)
        self.qkv, self.out = nn.Linear(width, width*3), nn.Linear(width,width)
        self.ff = nn.Sequential(nn.Linear(width,128), nn.GELU(), nn.Linear(128,width))
    def forward(self,x):
        b,n,_ = x.shape
        q,k,v = self.qkv(self.norm1(x)).reshape(b,n,3,self.heads,self.width//self.heads).permute(2,0,3,1,4).unbind(0)
        weights = torch.softmax(q @ k.transpose(-2,-1) / math.sqrt(self.width//self.heads),dim=-1)
        x = x + self.out((weights @ v).transpose(1,2).reshape(b,n,self.width))
        return x + self.ff(self.norm2(x))

class ThermalModel(nn.Module):
    def __init__(self, kind="transformer"):
        super().__init__()
        self.kind = kind
        if kind == "transformer":
            self.embed = nn.Linear(80,64)
            self.position = nn.Parameter(torch.randn(1,12,64)*.02)
            self.blocks = nn.Sequential(AttentionBlock(),AttentionBlock())
        else:
            self.conv = nn.Sequential(nn.Conv1d(8,32,5,padding=4,dilation=2),nn.ReLU(),
                                      nn.Conv1d(32,64,5,padding=8,dilation=4),nn.ReLU())
        self.head = nn.Sequential(nn.Linear(64,64),nn.ReLU(),nn.Linear(64,16))
    def forward(self,x):
        if self.kind == "transformer":
            h = self.blocks(self.embed(x.reshape(-1,12,80)) + self.position).mean(dim=1)
        else:
            h = self.conv(x.transpose(1,2)).mean(dim=2)
        y = self.head(h)
        # Quantile head predicts offsets from last temperature; ordered by construction.
        center = y[:,6:9] + x[:,-1,0:1]
        lower = center - torch.nn.functional.softplus(y[:,9:12]) * .1
        upper = center + torch.nn.functional.softplus(y[:,12:15]) * .1
        return y[:,:6], torch.stack([lower,center,upper],dim=-1), y[:,15:16]
