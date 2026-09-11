import type {
    BuildPaginationInput,
    PaginationInfo,
} from "@/pages/dashboard/media-owner/model/metricsTypes";

export const buildPaginationInfo = <T,>({
    rows,
    page,
    rowsPerPage,
}: BuildPaginationInput<T>): PaginationInfo<T> => {
    const totalPages = Math.max(1, Math.ceil(rows.length / rowsPerPage));
    const currentPage = Math.min(page, totalPages);
    const pagedRows = rows.slice(
        (currentPage - 1) * rowsPerPage,
        currentPage * rowsPerPage
    );

    return {
        totalPages,
        currentPage,
        rows: pagedRows,
    };
};
