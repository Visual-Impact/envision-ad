import { Grid, GridCol } from "@mantine/core";
import { MediaCard, SkeletonMediaCard, type MediaCardProps } from "@/entities/media";

interface CardGridProps {
    children?: React.ReactNode; 
}

function CardGrid({children}: CardGridProps) {
    return(
        <Grid w={"100%"}>
            {children}
        </Grid>
    )
}

interface MediaCardGridProps {
    id?: string;
    medias: MediaCardProps[]; 
    size: number
}

export function MediaCardGrid({id, medias, size}: MediaCardGridProps) {
    return(
            <CardGrid>
                {medias.map((media) => (
                    <GridCol key={id ? `${id}-${media.index}` : media.index} span={{base: 12 , xs: 6 * size, sm: 4 * size, md: 3 * size, lg: 3 * size}}>
                        <MediaCard
                            index={id ? id + media.index : media.index}
                            href={media.href}
                            imageUrl={media.imageUrl}
                            title={media.title}
                            organizationId={media.organizationId}
                            organizationName={media.organizationName}
                            mediaLocation={media.mediaLocation}
                            typeOfDisplay={media.typeOfDisplay}
                            price={media.price}
                            dailyImpressions={media.dailyImpressions}
                            schedule={media.schedule}
                            resolution={media.resolution}
                            venue={media.venue}
                        />
                    </GridCol>
              ))}
            </CardGrid>
    )
}

interface SkeletonMediaCardGridProps {
    count?: number;
    size: number;
}

export function SkeletonMediaCardGrid({ count = 8, size }: SkeletonMediaCardGridProps) {
    return (
        <CardGrid>
            {Array.from({ length: count }).map((_, i) => (
                <GridCol key={i} span={{ base: 12, xs: 6 * size, sm: 4 * size, md: 3 * size, lg: 3 * size }}>
                    <SkeletonMediaCard />
                </GridCol>
            ))}
        </CardGrid>
    );
}

export default CardGrid;