import { Title } from "@/ui/typography";

export default function Workbench() {
  return (
    <div className="min-h-[70vh] w-full flex items-center justify-center text-center bg-gradient-to-br from-cyan-50 via-white to-indigo-50 rounded-xl">
      <Title as="h2" align="center" className="text-4xl md:text-5xl font-extrabold leading-tight">
        <span className="bg-gradient-to-r from-sky-500 to-blue-600 bg-clip-text text-transparent">
          BI数智平台(机密)
        </span>
      </Title>
    </div>
  );
}
