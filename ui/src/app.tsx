import React, { useState } from 'react';
import { LayoutGrid, Rss, Settings, Search, Sparkles, ChevronRight } from 'lucide-react';

const mockArticles = [
    { id: 1, title: "深入理解向量数据库在 AI 领域的应用", summary: "本文探讨了 PgVector 在 RSS 订阅中的去重逻辑...", source: "技术周刊", category: "AI" },
    { id: 2, title: "NDSS 2026 论文投稿指南", summary: "针对隐私计算领域的最新投稿趋势分析...", source: "学术前沿", category: "Security" },
    { id: 3, title: "Java 21 新特性：虚拟线程实战", summary: "在高并发抓取场景下，如何利用虚拟线程优化 IO 性能...", source: "后端架构", category: "Java" },
];

export default function FeedApp() {
    const [selectedId, setSelectedId] = useState(1);

    return (
        <div className="flex h-screen w-full bg-[#F9F9F9] text-[#1A1A1A] font-sans selection:bg-orange-100">

            {/* 1. 左侧 Sidebar - 仿 Folo 极简图标 */}
            <aside className="w-16 flex flex-col items-center py-6 border-r border-gray-200 bg-white">
                <div className="w-10 h-10 bg-orange-500 rounded-xl flex items-center justify-center mb-8 shadow-lg shadow-orange-200 text-white font-bold">F</div>
                <nav className="flex flex-col gap-6 text-gray-400">
                    <LayoutGrid className="w-6 h-6 cursor-pointer hover:text-orange-500 transition-colors" />
                    <Search className="w-6 h-6 cursor-pointer hover:text-orange-500 transition-colors" />
                    <Rss className="w-6 h-6 text-orange-500" />
                    <div className="mt-auto pb-4">
                        <Settings className="w-6 h-6 cursor-pointer hover:text-orange-500" />
                    </div>
                </nav>
            </aside>

            {/* 2. 中间 Article List - 呼吸感卡片 */}
            <section className="w-80 border-r border-gray-200 overflow-y-auto bg-white">
                <header className="p-6 sticky top-0 bg-white/80 backdrop-blur-md z-10">
                    <h1 className="text-xl font-bold tracking-tight">智能精选</h1>
                    <p className="text-xs text-gray-400 mt-1 uppercase tracking-widest">Model B - Filtering</p>
                </header>

                <div className="px-3 pb-6">
                    {mockArticles.map((item) => (
                        <div
                            key={item.id}
                            onClick={() => setSelectedId(item.id)}
                            className={`p-4 mb-2 rounded-2xl cursor-pointer transition-all duration-300 ${
                                selectedId === item.id
                                    ? 'bg-orange-50/80 border border-orange-100 shadow-sm'
                                    : 'hover:bg-gray-50 border border-transparent'
                            }`}
                        >
                            <div className="flex items-center gap-2 mb-2">
                                <span className="text-[10px] px-2 py-0.5 bg-gray-100 text-gray-500 rounded-full font-medium">{item.category}</span>
                                <span className="text-[10px] text-gray-400">{item.source}</span>
                            </div>
                            <h2 className="text-sm font-semibold leading-snug mb-2">{item.title}</h2>
                            <p className="text-xs text-gray-500 line-clamp-2 leading-relaxed">{item.summary}</p>
                        </div>
                    ))}
                </div>
            </section>

            {/* 3. 右侧 Content View - Hybrid 模式展示区域 */}
            <main className="flex-1 overflow-y-auto bg-[#FDFDFD]">
                <article className="max-w-2xl mx-auto py-16 px-8">
                    <header className="mb-12">
                        <div className="flex items-center gap-2 text-orange-500 mb-6 bg-orange-50 w-fit px-3 py-1 rounded-full border border-orange-100">
                            <Sparkles className="w-4 h-4" />
                            <span className="text-xs font-bold uppercase tracking-wider">AI 智能摘要</span>
                        </div>
                        <h1 className="text-4xl font-extrabold leading-tight tracking-tight mb-4">
                            {mockArticles.find(a => a.id === selectedId)?.title}
                        </h1>
                        <p className="text-gray-400 text-sm">发布于 2026年3月25日 · 来源：{mockArticles.find(a => a.id === selectedId)?.source}</p>
                    </header>

                    {/* 模拟 Hybrid 模式：直接显示后端存储的摘要 */}
                    <div className="prose prose-orange max-w-none">
                        <div className="p-6 bg-white border border-gray-100 rounded-3xl shadow-sm italic text-gray-700 leading-loose text-lg">
                            " {mockArticles.find(a => a.id === selectedId)?.summary} "
                        </div>

                        {/* 提示前端按需抓取原文 */}
                        <div className="mt-12 pt-8 border-t border-gray-100 flex flex-col items-center">
                            <p className="text-gray-400 text-sm mb-4 italic">摘要已读完，正在从原始地址为您加载全文...</p>
                            <button className="flex items-center gap-2 px-6 py-3 bg-[#1A1A1A] text-white rounded-full text-sm font-medium hover:bg-orange-500 transition-all shadow-lg hover:shadow-orange-200 group">
                                阅读原文内容 <ChevronRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
                            </button>
                        </div>
                    </div>
                </article>
            </main>

        </div>
    );
}